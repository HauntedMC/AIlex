---
id: hauntedmc.raid-cooldown
title: Raid cooldown
aliases: [raid cooldown, raid blocked, anti raid farm]
category: gameplay
authority: implementation-confirmed
updated: 2026-08-29
source: ServerFeatures 3.7.1: docs/features/antiraidfarm.md
---

The server can limit how often one player starts a raid. If a repeated trigger is blocked, wait for the remaining cooldown shown in game before trying again.

The cooldown applies only after a raid successfully starts. It is local to the current server and can clear after a restart, so it is not a promised global timer.
