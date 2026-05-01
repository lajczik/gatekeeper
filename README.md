![gatekeeper](https://i.imgur.com/MiKqVbx.png)

**Gatekeeper** is a lightweight, highly optimized Minecraft plugin that defends your server from VPNs, proxies and abusive ISP ranges. It combines fast local ASN analysis with optional external IP checks to give you a multi-layered protection system that’s tuned for high connection volumes.

## Requirements:
* Java 11 or later
* Server version 1.8 - 26.1.2

## Features:
* Block VPNs and proxies (local + external layered detection)
* ASN / ISP blocking (block entire provider ranges)
* External IP verification via configurable APIs (fallback shield)
* Country detection and country-based rules
* Per-IP player limit (prevent account sharing and sockpuppets)
* Connection rate limiting (protect against rapid reconnects / attack vectors)
* Extremely lightweight and optimized for high concurrent connections
* Local ASN analysis — proxy detection in **under 1 millisecond** for most checks
* Configurable priorities and easy integration with permission systems

### Why choose Gatekeeper?
Gatekeeper combines speed and depth: local ASN analysis gives instant verdicts with tiny CPU cost, and the external API layer catches anything missed locally. That means fewer false negatives, minimal server load, and industry-grade features without a paywall.

### Quick install
1. Drop `Gatekeeper.jar` into your server's `plugins` folder.
2. Restart the server to generate the default config.
3. Edit `config.yml` to add API keys, ASN lists, rate limits and country rules.
4. Reload or restart.

### Recommended for
* Large public servers and networks
* Hubs and proxy-prone communities
* Admins who want advanced blocking (ASN, country, rate limits) with minimal resource use