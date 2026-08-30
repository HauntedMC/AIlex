---
id: hauntedmc.player-graves
title: Player graves
aliases: [grave, graveyard, death items, xp recovery, deathchest]
category: gameplay
authority: implementation-confirmed
updated: 2026-08-29
source: ServerFeatures 3.7.1: docs/features/graveyard.md | https://www.hauntedmc.nl/threads/grote-hauntedmc-update.16064/
---

When you die, a virtual grave can appear at the death location. It shows your name and remaining recovery time. Items and the experience Minecraft would normally drop are protected for **10 minutes** of active server time, so ordinary downtime does not consume the timer.

Use `/grave` to see your nearest active grave, or `/grave list` to list them. Where available, `/grave info <grave id>`, `/grave locate <grave id>`, `/grave track <grave id>`, `/grave track off`, and `/grave claim <grave id>` help you find or remotely claim one. A grave that cannot be safely shown at its death location can still be recoverable remotely.

Partial collection is safe: the system first tries original inventory slots, then compatible stacks and empty slots. It never overwrites inventory items or deliberately drops overflow; unclaimed items stay in the grave until recovered or expired.
