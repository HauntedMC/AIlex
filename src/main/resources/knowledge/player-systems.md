---
id: hauntedmc.player-systems
title: Current player systems: DeathChests, AutoPickup, messages, lottery and CombatTag
aliases: [deathchest, grave, graveyard, autopickup, drop2inventory, /msg mode, lottery, combattag, fly, god]
category: server-feature
authority: official
updated: 2026-08-29
expires: null
source: HauntedMC major network update 2026-08-04 + current public rank documentation
---

## DeathChests / Graveyards
After death, HauntedMC stores the player's items in a packet-based virtual grave at the death location instead of leaving ordinary item entities on the ground. The grave shows the player name and a remaining-time indicator.

The current documented default claim time is **10 minutes**. The grave stores the amount of experience Minecraft would normally drop on death. Partial claiming is safe: if the inventory is full, remaining items stay in the grave instead of being overwritten.

## AutoPickup
AutoPickup replaced Drop2Inventory. It is available from the `Legend` rank and is toggled with `/autopickup`.

It only applies to blocks the player directly breaks. When inventory space is exhausted, existing items are not overwritten and remaining drops fall normally; a free offhand slot can be used where possible. The setting is stored across HauntedMC servers.

## Private-message mode
`/msg mode` controls who may send private messages. Current choices are `FRIENDS` and `ALL`, and `FRIENDS` is the default for new players. Staff can still reach players when necessary. Blocked players and vanished staff are handled without exposing hidden staff presence.

## Lottery
The modernized Lottery revolves around tickets, a jackpot, donations, statistics and displayed win chance. `/lottery buy` is the documented purchase route. Ticket price, jackpot amount, draw time, odds and current entries are live values and must not be invented.

## FairPerks and CombatTag
Rank-dependent perks include `/fly` and `/god`. Flight is disabled when a player receives CombatTag. Server/game-mode safety logic can also disable these perks in contexts such as dungeon worlds or restricted worlds.
