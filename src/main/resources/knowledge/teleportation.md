---
id: hauntedmc.teleportation
title: Random and coordinate teleport
aliases: [/randomtp, /rtp, /tppos, random teleport, teleport coordinates]
category: gameplay
authority: implementation-confirmed
updated: 2026-08-29
source: ServerFeatures 3.7.1: docs/features/teleportation.md | https://www.hauntedmc.nl/threads/survival-rank-functies-en-commandos.15684/ | https://www.hauntedmc.nl/threads/creative-rank-functies-en-commandos.15685/
---

Use `/randomtp` or `/rtp` to find a random location in your current world. The search respects the configured or world-border area, avoids the configured inner area, rejects unsafe terrain, and can avoid claims. It may fail if no suitable location is found in the allowed area.

Use `/tppos <x> <y> <z>` to teleport to whole-number coordinates in your current world. The current Survival and Creative rank pages list this command for everyone; the live command permission and world policy still decide whether it is available. Relative coordinates (`~`), decimals, and a world argument are not supported. If the requested position is blocked, the command searches upward at that X/Z for two air blocks without lava immediately below.

Random teleport and coordinate teleport have separate cooldowns when configured. They have no built-in cost or warmup. A successful teleport can update Essentials `/back` when that service is available.
