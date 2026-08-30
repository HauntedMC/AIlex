---
id: hauntedmc.combat-tag
title: CombatTag
aliases: [combattag, combat tag, combat logging, combat teleport, /combattag status]
category: gameplay
authority: implementation-confirmed
updated: 2026-08-29
source: ServerFeatures 3.7.1: docs/features/combattag.md
---

CombatTag starts after qualifying combat damage and refreshes when another qualifying hit lands. It can apply to player combat, hostile-mob combat, or both according to the current game-mode policy. Projectiles, pets, TNT, and similar indirect damage can count as combat.

While tagged, ordinary teleports and portals can be blocked, and logging out can be punished under the current server policy. Server shutdown is not treated as combat logging. If available to you, `/combattag status` shows your tag state; do not assume a fixed combat duration because it is configured live.
