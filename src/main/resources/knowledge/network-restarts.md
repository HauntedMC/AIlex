---
id: hauntedmc.network-restarts
title: Server restarts and automatic return
aliases: [restart, autoreconnect, /autoreconnect cancel, fallback, limbo]
category: network
authority: implementation-confirmed
updated: 2026-08-29
source: ProxyFeatures 3.7.1: docs/features/restart.md | https://www.hauntedmc.nl/threads/grote-hauntedmc-update.16064/
---

During a planned game-mode restart, you can be moved to a fallback server. When the game mode is ready, HauntedMC can reconnect eligible players gradually. Use `/autoreconnect cancel` to opt out before the reconnect starts. Moving to another server or disconnecting also opts you out of that restart's return.

Automatic return follows the normal connection rules, so a full, maintained, or unavailable target can still prevent it. Restart timing and the fallback destination are live; do not estimate them.
