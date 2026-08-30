---
id: hauntedmc.spawn
title: Spawn
aliases: [/spawn, /spawn cancel, spawn warmup, world spawn]
category: gameplay
authority: implementation-confirmed
updated: 2026-08-29
source: ServerFeatures 3.7.1: docs/features/spawn.md
---

Use `/spawn` to go to the server's normal spawn. Use `/spawn cancel` to cancel a pending spawn warmup. A new `/spawn` request replaces an older pending request.

Where you have access, `/spawn <world>` goes to that loaded world's native spawn. Spawn may use a warmup or cooldown, and movement, damage, teleporting, death, or quitting can cancel a warmup depending on the current server policy. A failed or cancelled spawn request does not start its cooldown.
