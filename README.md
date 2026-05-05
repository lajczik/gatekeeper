<div align="center">
  <img src="https://i.imgur.com/HgOUBog.png" alt="LagFixer Logo" width="600"/>

  <br>
  <br>

  <p>
    <a href="https://modrinth.com/plugin/gatekeeper-mc"><img src="https://img.shields.io/badge/Download-Modrinth-00AF5C?style=for-the-badge&logo=modrinth" alt="Modrinth"></a>
    <a href="https://discord.gg/CFmzJjgZdu"><img src="https://img.shields.io/badge/Support-Discord-5865F2?style=for-the-badge&logo=discord" alt="Discord"></a>
    <a href="https://github.com/lajczik/gatekeeper"><img src="https://img.shields.io/badge/Source-GitHub-181717?style=for-the-badge&logo=github" alt="GitHub"></a>
  </p>

  <p>Gatekeeper is a lightweight, highly optimized Minecraft plugin that defends your server from VPNs, proxies and abusive ISP ranges. It combines fast local ASN analysis with optional external IP checks to give you a multi-layered protection system that’s tuned for high connection volumes.</p>
</div>

## ⚡ Requirements & Compatibility
- **Java:** 11 or later
- **Server:** Bukkit (Spigot, Paper, Purpur etc) / Bungeecord / Velocity
- **Version range:** `1.8` – `26.1.2`

## 🧩 Modules Overview

| Module | Description & Features |
| :--- | :--- |
| **AccountLimit** | Keets your server fair by limiting how many accounts can connect from a single IP – perfect for stopping alt abuse. |
| **AntiVpn** | Scans connections through an external API to catch even the most sneaky VPNs and proxies – extremely accurate, with a tiny trade-off in speed for that extra peace of mind. |
| **AsnFilter** | Blocks traffic from dangerous or suspicious ASNs in milliseconds – not quite as precise as a full VPN detector, but blazing fast and catches most bad actors instantly. |
| **Blacklist** | Gives you full control to permanently ban any IP or username – simple, reliable, and instantly effective. |
| **CountryFilter** | Instantly blocks entire countries you don’t trust – clean and ruthless traffic filtering with zero lag. |
| **RateLimit** | Prevents connection floods and brute-force attacks by limiting how often clients can knock on your server’s door – smooth and stable, even under heavy fire. |

## Why choose Gatekeeper?
Gatekeeper gives you the best of both worlds: **speed and precision**. Local ASN analysis delivers a verdict in milliseconds with almost zero CPU cost. The external API catches anything the local check misses. The result? Fewer false negatives, minimal resource usage, and enterprise-grade features – without a paywall.

## Quick install
1. Drop `Gatekeeper.jar` into your server's `plugins` folder.
2. Restart the server – the default config will be generated automatically.
3. Edit module configs to add API keys, ASN lists, rate limits, and country rules.
4. Run `/gatekeeper reload` or restart your server.

## Who is Gatekeeper for?
✅ Large public servers and networks  
✅ Hubs and communities prone to proxy abuse  
✅ Admins who want advanced blocking (ASN, country, rate limits) without tanking performance

> 💡 **One last thing** – Gatekeeper works right out of the box. But its true power unlocks after just 5 minutes of configuration: add your API keys, tweak your ASN lists, set your limits. That's when your server becomes an impenetrable fortress, and your players will feel the difference. Join us on Discord and see how easy it is to keep your lobby clean.