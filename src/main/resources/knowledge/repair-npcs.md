---
id: hauntedmc.repair-npcs
title: Repair NPCs
aliases: [repair npc, blacksmith, repair item, repair quote]
category: gameplay
authority: implementation-confirmed
updated: 2026-08-29
source: ServerFeatures 3.7.1: docs/features/repairnpc.md
---

Right-click a repair NPC while holding a supported damaged item to receive a repair quote. Right-click the same NPC again with the same item to confirm. The price can depend on the item, current damage, and enchantments, so it is recalculated when you confirm.

After payment, the NPC repairs the item and returns it after the configured delay. Do not swap or modify the quoted item before confirmation. Normal vanilla tools, weapons, armour, Elytra, and several common utility items are supported; custom or unsupported damageable items are not.
