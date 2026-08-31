---
id: hauntedmc.player-ping
title: Ping
aliases: [/ping, /connectioninfo, ping, latency, connection information]
category: connection
authority: implementation-confirmed
updated: 2026-08-31
source: Current ProxyFeatures command implementation: features/connectioninfo/command/PingCommand.java | ConnectionInfoCommand.java
---

The current Global Rank page lists `/ping` from Elite. The command is available only when ConnectionInfo is enabled and your account has access to it. It reports your current latency in milliseconds to the HauntedMC proxy, so the value can change moment to moment.

`/ping <player>` requires separate access and only works for an online player. It shows that player's current proxy latency; it does not reveal their location or provide a long-term connection-quality measurement.

`/connectioninfo` shows the connection details that the server makes available for your own current connection. `/connectioninfo <player>` is a separate, higher-access lookup for an online player and can expose sensitive connection data, so the bot must not suggest it as a routine diagnostic for someone else.
