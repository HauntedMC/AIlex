---
id: hauntedmc.dungeons
title: Survival Dungeons
aliases: [dungeon, dungeons, dungeon key, /warp dungeons, trial chamber, end city, bastion]
category: gamemode-feature
authority: reviewed
updated: 2026-08-29
expires: null
source: HauntedMC Dungeon update + current public Dungeons project reviewed 2026-08-29
---

## What Dungeons are
Dungeons are a Survival feature built around separate replayable dungeon worlds/maps. Public HauntedMC descriptions explain that players can explore, fight, loot and complete content containing structures, mobs and rewards outside the normal persistent Survival worlds. A major design goal is making structure-style content repeatedly accessible instead of letting finite world structures be exhausted by early players.

The current public Dungeons implementation supports configurable dungeon content with queueing, teams, triggers/functions, difficulty scaling and loot-table rewards. Exact player-facing availability is still determined by the live Survival dungeon hub.

## Starting a dungeon
The documented player hub is `/warp dungeons`. Dungeon NPCs represent available dungeons. Starting a dungeon requires the matching Dungeon Key or other access requirement shown by the current hub. Keys have been obtainable through HauntedMC reward/store systems including Tokens/Dungeon Crate routes, but exact current acquisition, costs and odds are volatile and must be checked live.

Do not expose administrative dungeon-editor commands as normal player commands. The current player-safe route is the hub/NPC flow unless the live server tells the player otherwise.

## Leaving, death and restrictions
`/dungeon leave` is the documented leave command; older/current reviewed behaviour says leaving can consume the key, so warn players before suggesting it. Death returns the player through the configured dungeon/death flow and HauntedMC's protected death-item systems may apply. Rank invulnerability/mobility perks such as `/fly` or `/god` can be disabled in dungeon/combat contexts.

## Known dungeon themes
Reviewed public material has named Trial Chamber, End City and Bastion dungeon content. Treat those as known documented dungeon themes, not a guaranteed exhaustive live list. The dungeon hub or current announcement is the source of truth for which maps and difficulties are available now.