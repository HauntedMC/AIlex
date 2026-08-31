---
id: hauntedmc.network-navigation
title: Moving around the network
aliases: [/server, /hub, server command, change server, lobby, destination]
category: network
authority: implementation-confirmed
updated: 2026-08-31
source: Current ProxyFeatures command implementation: features/router/command/ServerCommand.java | docs/features/router.md
---

Use `/server` to see the public destinations you are currently allowed to join. Use `/server <destination>` to connect to one. The list is live: a destination can disappear when it is unavailable, full, in maintenance, or not available to your rank.

`/hub` is not a fixed standalone command anymore. It can exist as an alias for a configured Router destination (normally the lobby); use it only when it appears in your command suggestions. The same applies to any configured destination shortcut such as `/survival`: do not guess shortcut names. Every route still follows normal capacity, queue, maintenance, and connection checks.
