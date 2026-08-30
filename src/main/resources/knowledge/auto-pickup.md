---
id: hauntedmc.auto-pickup
title: AutoPickup
aliases: [autopickup, drop2inventory, /autopickup]
category: gameplay
authority: implementation-confirmed
updated: 2026-08-29
source: ServerFeatures 3.7.1: docs/features/autopickup.md | https://www.hauntedmc.nl/threads/survival-rank-functies-en-commandos.15684/
---

AutoPickup replaces Drop2Inventory. The current Survival Rank page lists it from Legend. Eligible players can use `/autopickup`, `/autopickup on`, `/autopickup off`, `/autopickup toggle`, or `/autopickup status`.

It applies only to items from blocks you directly break—not entity drops, explosions, pistons, fire, fluid, leaf decay, manual drops, or WorldEdit. AutoPickup uses only the 36 normal inventory-storage slots: never armour, offhand, crafting, cursor, or result slots. If part of a stack does not fit, that exact remainder stays on its original ground item entity.

Your choice normally survives logout and a local restart, but it is stored per backend and is not synchronized to other servers. The feature itself can be enabled on any current game mode.
