[English](https://github.com/heiher/sockstun/blob/main/README.md ) |  [Русский](https://github.com/heiher/sockstun/blob/main/README_RU.md)
# SocksTun

[![статус](https://github.com/heiher/sockstun/actions/workflows/build.yaml/badge.svg?branch=main&event=push)](https://github.com/heiher/sockstun)

Простая и лёгкая VPN поверх socks5 прокси для Android. Основана на высокопроизводительном и малозатратном [tun2socks](https://github.com/heiher/hev-socks5-tunnel).

[<img src="https://github.com/heiher/sockstun/blob/main/.github/badges/get-it-on.png"
    alt="Скачать на GitHub"
    height="80">](https://github.com/heiher/sockstun/releases)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Скачать на F-Droid"
    height="80">](https://f-droid.org/packages/hev.sockstun)

## Особенности

* Перенаправление TCP-соединений.
* Перенаправление UDP-пакетов. (Fullcone NAT, UDP-in-UDP и UDP-in-TCP [^1])
* Простая аутентификация по имени пользователя и паролю.
* Задание DNS-адресов.
* Двойной стек IPv4/IPv6.
* Глобальный режим и режим по приложению.

## Как собрать

Сделайте форк проекта и создайте новый релиз, либо соберите вручную:

```
git clone --recursive https://github.com/heiher/sockstun
cd sockstun
gradle assembleDebug
```

## Socks5 сервер

### UDP ретрансляция через TCP

```
git clone --recursive https://github.com/heiher/hev-socks5-server
cd hev-socks5-server
make

hev-socks5-server conf.yml
```

```
main:
  workers: 4
  port: 1080
  listen-address: '::'

misc:
  limit-nofile: 65535
```

### UDP ретрансляция через UDP

Любой socks5 сервер, реализующий методы CONNECT и UDP-ASSOCIATE согласно RFC1928.

## Зависимости

* HevSocks5Tunnel - https://github.com/heiher/hev-socks5-tunnel

## Авторы

* **hev** - https://hev.cc
* **ziqi mo** - https://github.com/mosentest

## Лицензия

MIT

[^1]: См. [спецификацию протокола](https://github.com/heiher/hev-socks5-core/tree/main?tab=readme-ov-file#udp-in-tcp). Сервер [hev-socks5-server](https://github.com/heiher/hev-socks5-server) поддерживает UDP ретрансляцию через TCP.
