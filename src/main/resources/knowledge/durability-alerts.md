---
id: hauntedmc.durability-alerts
title: Durability alerts
aliases: [durability alert, low durability, item warning]
category: gameplay
authority: implementation-confirmed
updated: 2026-08-29
source: ServerFeatures 3.7.1: docs/features/durabilityalert.md
---

DurabilityAlert can show an action-bar warning and sound when a damageable item reaches the server's low-durability threshold. It is a warning only: it does not change item damage, stop the item breaking, or repair it.

Once an item is below the threshold, each further qualifying damage event can warn you again. Repair or replace it before it breaks.
