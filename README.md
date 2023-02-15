# SocksTun

A simple and lightweight VPN over socks5 proxy for Android. It is based on a high-performance and low-overhead [tun2socks](https://github.com/heiher/hev-socks5-tunnel).

## Features

* Redirect TCP connections.
* Redirect UDP packets. (Fullcone NAT, UDP in UDP/TCP)
* Simple username/password authentication.
* Specifying DNS addresses.
* IPv4/IPv6 dual stack.
* Global/per-App modes.

## How to Build

```bash
git clone --recursive https://github.com/heiher/sockstun
cd sockstun
gradle assembleDebug
```

## Server side

```bash
git clone --recursive https://github.com/heiher/hev-socks5-server
cd hev-socks5-server
make

hev-socks5-server conf.yml
```

```yaml
main:
  workers: 4
  port: 1080
  listen-address: '::'

misc:
  limit-nofile: 65535
```

## Dependencies

* HevSocks5Tunnel - https://github.com/heiher/hev-socks5-tunnel

## Contributors

* **hev** - https://hev.cc

## License

MIT
