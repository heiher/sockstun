/*
 ============================================================================
 Name        : Socks5HttpBridge.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2024 xyz
 Description : Local SOCKS5 front-end that forwards connections to an
               upstream HTTP proxy using the HTTP CONNECT method (with
               optional Basic authentication). This lets the SOCKS5-only
               tunnel engine make use of an HTTP proxy.

               Note: HTTP CONNECT carries TCP only, so UDP traffic cannot
               be relayed in this mode (UDP ASSOCIATE requests are rejected).
 ============================================================================
 */

package hev.sockstun;

import android.net.VpnService;
import android.util.Base64;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class Socks5HttpBridge {
	private static final int CONNECT_TIMEOUT_MS = 15000;
	private static final int HANDSHAKE_TIMEOUT_MS = 30000;
	private static final int BUFFER_SIZE = 65536;
	private static final int MAX_HEADER_SIZE = 16384;

	private final VpnService vpnService;
	private final String proxyHost;
	private final int proxyPort;
	private final String proxyAuth; /* "Basic <base64>" or null when no credentials */

	private ServerSocket serverSocket;
	private Thread acceptThread;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public Socks5HttpBridge(VpnService vpnService, String proxyHost, int proxyPort,
			String proxyUser, String proxyPass) {
		this.vpnService = vpnService;
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
		if (proxyUser != null && !proxyUser.isEmpty()) {
			String creds = proxyUser + ":" + (proxyPass == null ? "" : proxyPass);
			this.proxyAuth = "Basic " + Base64.encodeToString(creds.getBytes(), Base64.NO_WRAP);
		} else {
			this.proxyAuth = null;
		}
	}

	/* Starts the local SOCKS5 server on loopback and returns the bound port. */
	public int start() throws IOException {
		serverSocket = new ServerSocket();
		serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
		int port = serverSocket.getLocalPort();
		running.set(true);
		acceptThread = new Thread(new Runnable() {
			@Override
			public void run() {
				acceptLoop();
			}
		}, "socks5-http-bridge");
		acceptThread.setDaemon(true);
		acceptThread.start();
		return port;
	}

	public void stop() {
		running.set(false);
		ServerSocket ss = serverSocket;
		serverSocket = null;
		if (ss != null) {
			try { ss.close(); } catch (IOException e) {}
		}
	}

	private void acceptLoop() {
		while (running.get()) {
			final Socket client;
			try {
				client = serverSocket.accept();
			} catch (IOException e) {
				break;
			}
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					handle(client);
				}
			}, "socks5-http-conn");
			t.setDaemon(true);
			t.start();
		}
	}

	private void handle(Socket client) {
		Socket upstream = null;
		try {
			client.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
			DataInputStream in = new DataInputStream(client.getInputStream());
			OutputStream out = client.getOutputStream();

			/* SOCKS5 greeting: VER, NMETHODS, METHODS. */
			int ver = in.readUnsignedByte();
			if (ver != 0x05) {
				client.close();
				return;
			}
			int nmethods = in.readUnsignedByte();
			byte[] methods = new byte[nmethods];
			in.readFully(methods);
			/* Reply: version 5, no authentication required. */
			out.write(new byte[] { 0x05, 0x00 });
			out.flush();

			/* SOCKS5 request: VER, CMD, RSV, ATYP, DST.ADDR, DST.PORT. */
			int rver = in.readUnsignedByte();
			int cmd = in.readUnsignedByte();
			in.readUnsignedByte(); /* RSV */
			int atyp = in.readUnsignedByte();
			String host;
			switch (atyp) {
				case 0x01: { /* IPv4 */
					byte[] a = new byte[4];
					in.readFully(a);
					host = InetAddress.getByAddress(a).getHostAddress();
					break;
				}
				case 0x03: { /* domain name */
					int len = in.readUnsignedByte();
					byte[] d = new byte[len];
					in.readFully(d);
					host = new String(d, "US-ASCII");
					break;
				}
				case 0x04: { /* IPv6 */
					byte[] a = new byte[16];
					in.readFully(a);
					host = "[" + InetAddress.getByAddress(a).getHostAddress() + "]";
					break;
				}
				default:
					replyError(out, 0x08); /* address type not supported */
					client.close();
					return;
			}
			int port = in.readUnsignedShort();

			/* Only CONNECT is supported; UDP ASSOCIATE/BIND cannot be tunnelled
			   through an HTTP proxy. */
			if (rver != 0x05 || cmd != 0x01) {
				replyError(out, 0x07); /* command not supported */
				client.close();
				return;
			}

			/* Open the upstream HTTP proxy connection and issue CONNECT. */
			upstream = new Socket();
			try {
				upstream.connect(new InetSocketAddress(proxyHost, proxyPort), CONNECT_TIMEOUT_MS);
			} catch (IOException e) {
				replyError(out, 0x05); /* connection refused */
				client.close();
				return;
			}
			protect(upstream);
			upstream.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

			boolean established;
			try {
				established = httpConnect(upstream, host, port);
			} catch (IOException e) {
				established = false;
			}
			if (!established) {
				replyError(out, 0x05); /* connection refused / proxy rejected */
				closeQuietly(upstream);
				client.close();
				return;
			}

			/* Success reply: bound address/port are unused, send zeros. */
			out.write(new byte[] { 0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0 });
			out.flush();

			/* Relay for the lifetime of the connection. */
			client.setSoTimeout(0);
			upstream.setSoTimeout(0);
			relay(client, upstream);
		} catch (IOException e) {
			/* Fall through to cleanup. */
		} finally {
			closeQuietly(upstream);
			closeQuietly(client);
		}
	}

	/* Sends the CONNECT request and parses the status line. Returns true on 2xx. */
	private boolean httpConnect(Socket upstream, String host, int port) throws IOException {
		String target = host + ":" + port;
		StringBuilder req = new StringBuilder();
		req.append("CONNECT ").append(target).append(" HTTP/1.1\r\n");
		req.append("Host: ").append(target).append("\r\n");
		if (proxyAuth != null)
			req.append("Proxy-Authorization: ").append(proxyAuth).append("\r\n");
		req.append("Proxy-Connection: keep-alive\r\n");
		req.append("\r\n");

		OutputStream os = upstream.getOutputStream();
		os.write(req.toString().getBytes("US-ASCII"));
		os.flush();

		/* Read the response headers one byte at a time, stopping exactly at the
		   end-of-headers boundary so tunnelled payload is left untouched. */
		InputStream is = upstream.getInputStream();
		StringBuilder header = new StringBuilder();
		int c;
		while ((c = is.read()) != -1) {
			header.append((char) c);
			int len = header.length();
			boolean end =
				(len >= 2 && header.charAt(len - 2) == '\n' && header.charAt(len - 1) == '\n') ||
				(len >= 4 && header.charAt(len - 4) == '\r' && header.charAt(len - 3) == '\n'
					&& header.charAt(len - 2) == '\r' && header.charAt(len - 1) == '\n');
			if (end)
				break;
			if (len > MAX_HEADER_SIZE)
				return false;
		}

		String resp = header.toString();
		int sp = resp.indexOf(' ');
		if (sp < 0)
			return false;
		int sp2 = resp.indexOf(' ', sp + 1);
		String code = (sp2 > sp) ? resp.substring(sp + 1, sp2) : resp.substring(sp + 1).trim();
		return code.startsWith("2");
	}

	private void relay(Socket a, Socket b) {
		final Socket from = a;
		final Socket to = b;
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				pipe(from, to);
			}
		}, "socks5-http-relay");
		t.setDaemon(true);
		t.start();
		pipe(b, a);
		try { t.join(); } catch (InterruptedException e) {}
	}

	private void pipe(Socket from, Socket to) {
		byte[] buf = new byte[BUFFER_SIZE];
		try {
			InputStream in = from.getInputStream();
			OutputStream out = to.getOutputStream();
			int n;
			while ((n = in.read(buf)) != -1) {
				out.write(buf, 0, n);
				out.flush();
			}
		} catch (IOException e) {
			/* Peer closed or error; tear both ends down below. */
		} finally {
			closeQuietly(from);
			closeQuietly(to);
		}
	}

	private void protect(Socket socket) {
		try {
			vpnService.protect(socket);
		} catch (Throwable t) {
			/* Best effort: the app is already excluded from its own tunnel, so
			   these sockets bypass the VPN even if protect() is unavailable. */
		}
	}

	private static void replyError(OutputStream out, int rep) {
		try {
			out.write(new byte[] { 0x05, (byte) rep, 0x00, 0x01, 0, 0, 0, 0, 0, 0 });
			out.flush();
		} catch (IOException e) {}
	}

	private static void closeQuietly(Socket s) {
		if (s != null) {
			try { s.close(); } catch (IOException e) {}
		}
	}
}
