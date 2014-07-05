flowdock-xmpp
=============

Unofficial Flowdock XMPP (Jabber) Gateway

## Usage

You can connect to flowdock-xmpp using your personal
[API token](https://www.flowdock.com/account/tokens) in your JID (you can pick
any value for your password).

If you are not eager to run your own instance of flowdock-xmpp you can connect
to a public one by using `<API_TOKEN>@flowdock.ozansener.com`.


## Installation

### Prerequisites

First you might want to install `libicu-dev` package for your operating
system. See the installation guide at
http://node-xmpp.github.io/doc/nodestringprep.html for instructions. If you
skip this step a slower JavaScript fallback is used instead.


### Getting flowdock-xmpp

You can install flowdock-xmpp using `npm install -g flowdock-xmpp`.

### Building it manually

After you install [leiningen](http://leiningen.org/) you can build and run
flowdock-xmpp using
```sh
lein cljsbuild once
node bin/flowdock-xmpp.js
```

### Possible environment configuration

- *FLOWDOCK_XMPP_DOMAIN*: the domain you want to use for XMPP JIDs (defaults to
system hostname).

- *FLOWDOCK_XMPP_KEY*: path to a key file to be used for TLS connections
(optional).

- *FLOWDOCK_XMPP_CERT*: path to a cert file to be used for TLS connections
(optional).


## Disclaimer

This project is not affiliated with Flowdock. Use it at your own risk.
