/*
 ============================================================================
 Name        : LocalNetwork.java
 Copyright   : Copyright (c) 2026 xyz
 Description : Local network route exclusion
 ============================================================================
 */

package hev.sockstun;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

/* Route set for the tun interface.

   The tunnel normally claims the whole address space with a single default
   route. When "ignore local network" is on we install the complement of the
   on-link prefixes of every non-VPN network instead, so LAN traffic keeps
   using the physical interface and everything else is still tunnelled.

   VpnService.Builder.excludeRoute() would express this directly but only
   exists on API 33+, so the complement is computed here for every version. */
public class LocalNetwork {
	private static final byte[] ANY_V4 = new byte[4];
	private static final byte[] ANY_V6 = new byte[16];

	/* An address prefix held as raw bytes, so v4 and v6 share one code path. */
	public static class Prefix {
		private final byte[] addr;
		private final int length;

		Prefix(byte[] addr, int length) {
			this.addr = mask(addr, length);
			this.length = length;
		}

		public int getLength() {
			return length;
		}

		public InetAddress getAddress() throws UnknownHostException {
			return InetAddress.getByAddress(addr);
		}

		private static byte[] mask(byte[] addr, int length) {
			byte[] out = addr.clone();
			for (int i = length; i < out.length * 8; i++)
			  out[i / 8] &= ~(1 << (7 - (i % 8)));
			return out;
		}

		private int bit(int i) {
			return (addr[i / 8] >> (7 - (i % 8))) & 1;
		}

		/* True when the two prefixes share every bit up to the shorter
		   length, i.e. one is an ancestor of the other or they are equal. */
		private boolean overlaps(Prefix other) {
			int common = Math.min(length, other.length);
			if (addr.length != other.addr.length)
			  return false;
			for (int i = 0; i < common; i++)
			  if (bit(i) != other.bit(i))
				return false;
			return true;
		}

		private boolean contains(Prefix other) {
			return length <= other.length && overlaps(other);
		}

		/* The sibling subtree at depth (index + 1) of the path to this
		   prefix -- everything under it is disjoint from this prefix. */
		private Prefix sibling(int index) {
			byte[] out = addr.clone();
			out[index / 8] ^= (1 << (7 - (index % 8)));
			return new Prefix(out, index + 1);
		}
	}

	/* Routes to install for the given family with the local network cut out.
	   Falls back to the default route when nothing would be left. */
	public static List<Prefix> routes(Context context, boolean ipv6) {
		byte[] any = ipv6 ? ANY_V6 : ANY_V4;
		List<Prefix> space = new ArrayList<Prefix>();
		space.add(new Prefix(any, 0));

		List<Prefix> routes = exclude(space, excluded(context, ipv6));
		if (routes.isEmpty())
		  routes = space;
		return routes;
	}

	/* Prefixes that should bypass the tunnel: every on-link prefix of every
	   non-VPN network, plus the well-known link-local and multicast ranges
	   that local device discovery (mDNS, SSDP) relies on. */
	private static List<Prefix> excluded(Context context, boolean ipv6) {
		List<Prefix> out = new ArrayList<Prefix>();

		if (ipv6) {
			out.add(new Prefix(new byte[] {(byte) 0xfe, (byte) 0x80, 0, 0, 0, 0, 0, 0,
										   0, 0, 0, 0, 0, 0, 0, 0}, 10));
			out.add(new Prefix(new byte[] {(byte) 0xff, 0, 0, 0, 0, 0, 0, 0,
										   0, 0, 0, 0, 0, 0, 0, 0}, 8));
		} else {
			out.add(new Prefix(new byte[] {(byte) 224, 0, 0, 0}, 4));
		}

		ConnectivityManager cm = (ConnectivityManager)
			context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm == null)
		  return out;

		for (Network network : cm.getAllNetworks()) {
			NetworkCapabilities caps = cm.getNetworkCapabilities(network);
			if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
			  continue;
			LinkProperties link = cm.getLinkProperties(network);
			if (link == null)
			  continue;
			for (LinkAddress address : link.getLinkAddresses()) {
				InetAddress addr = address.getAddress();
				int length = address.getPrefixLength();
				if (addr.isLoopbackAddress() || addr.isAnyLocalAddress())
				  continue;
				if ((addr instanceof Inet4Address) == ipv6)
				  continue;
				/* A zero-length prefix would swallow the whole space and a
				   host route covers only this device -- neither is a LAN. */
				if (length < 1 || length >= addr.getAddress().length * 8)
				  continue;
				out.add(new Prefix(addr.getAddress(), length));
			}
		}
		return out;
	}

	/* Subtract each excluded prefix from the space, splitting any enclosing
	   prefix into the siblings along the path down to it. Excluding a /24
	   from 0.0.0.0/0 yields 24 routes. */
	private static List<Prefix> exclude(List<Prefix> space, List<Prefix> excluded) {
		List<Prefix> routes = space;

		for (Prefix cut : excluded) {
			List<Prefix> next = new ArrayList<Prefix>();
			for (Prefix route : routes) {
				if (!route.overlaps(cut)) {
					next.add(route);
					continue;
				}
				if (cut.contains(route))
				  continue;
				for (int i = route.length; i < cut.length; i++)
				  next.add(cut.sibling(i));
			}
			routes = next;
		}
		return routes;
	}
}
