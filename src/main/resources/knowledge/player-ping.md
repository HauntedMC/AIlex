---
id: hauntedmc.player-ping
title: Ping
aliases: [/ping, ping, latency]
category: connection
authority: implementation-confirmed
updated: 2026-08-29
source: ProxyFeatures 3.7.1: docs/features/connectioninfo.md | https://www.hauntedmc.nl/threads/globale-rank-functies-en-commandos.15683/
---

The current Global Rank page lists `/ping` from Elite. The command is available only when ConnectionInfo is enabled and your account has access to it. It reports your current latency in milliseconds to the HauntedMC proxy, so the value can change moment to moment.

`/ping <player>` requires separate access and only works for an online player. It shows that player's current proxy latency; it does not reveal their location or provide a long-term connection-quality measurement.
