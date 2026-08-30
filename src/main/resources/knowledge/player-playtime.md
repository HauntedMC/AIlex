---
id: hauntedmc.player-playtime
title: Playtime
aliases: [/playtime, playtime total, playtime top, speeltijd]
category: profile
authority: implementation-confirmed
updated: 2026-08-29
source: ProxyFeatures 3.7.1: docs/features/playtime.md
---

Use `/playtime` to see your visible game-mode playtime and total. Use `/playtime total` for only the total, or `/playtime <gamemode>` for one logical game mode, such as `/playtime survival`.

Use `/playtime total <player>` or `/playtime <gamemode> <player>` to look up another known player when that access is available, including an offline player. Use `/playtime top` for the total leaderboard or `/playtime top <gamemode>` for a game-mode leaderboard.

`total` and `top` are reserved words: `/playtime <player>` is intentionally not a valid player lookup. A game mode is a logical mode, not a physical backend name, and hidden modes may not be available in these commands.
