---
id: hauntedmc.network-navigation
title: Moving around the network
aliases: [/hub, hub, server command, /survival, change server, lobby]
category: network
authority: implementation-confirmed
updated: 2026-08-29
source: ProxyFeatures 3.7.1: docs/features/hub.md | docs/features/slashserver.md
---

Use `/hub` to return to the configured network hub. It reports if the hub is unavailable or you are already there.

HauntedMC can also provide a direct command for an enabled backend: for example, a backend named `survival` has `/survival`. These commands are the backend's exact name, so do not translate or guess one. A direct server command still follows normal capacity, queue, maintenance, and connection checks.
