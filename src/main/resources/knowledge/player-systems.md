---
id: hauntedmc.player-systems
title: Current player systems: DeathChests, AutoPickup, messages, lottery, CombatTag and rank perks
aliases: [deathchest, grave, graveyard, autopickup, drop2inventory, /msg mode, lottery, combattag, fly, god]
category: server-feature
authority: official
updated: 2026-08-26
expires: null
source: https://www.hauntedmc.nl/threads/grote-hauntedmc-update.16064/
---

## DeathChests
After death, items are protected through HauntedMC's death-chest/virtual-grave system rather than simply being left as ordinary ground drops. The grave is associated with the death location. Interacting with it recovers available items and experience; if inventory space is insufficient, remaining contents stay protected instead of being silently overwritten.

Do not promise an exact grave lifetime, cross-world behaviour or recovery fee unless current live/help evidence provides it.

## AutoPickup
AutoPickup replaced the older Drop2Inventory naming/system. Eligible players can toggle it with `/autopickup`. The reviewed August 2026 update explicitly documents AutoPickup for eligible Legend-rank players and blocks they personally break. It does not overwrite an inventory: excess drops fall normally when there is no room.

Rank eligibility can change, so `/ranks` is stronger evidence for the current perk matrix than an old update post.

## Private-message mode
`/msg mode` controls who may send a player private messages. Reviewed current choices are `FRIENDS` and `ALL`, with `FRIENDS` documented as the default for new players. Other message controls include toggle/block/unblock routes documented in general commands.

## Lottery
The modernized Lottery revolves around tickets, a jackpot, donations, statistics and displayed win chance. `/lottery buy` is the documented purchase route. Ticket price, jackpot amount, draw time, odds and current entries are live/volatile: never invent or cache those values as permanent knowledge.

## FairPerks and CombatTag
FairPerks provides rank-dependent `/fly` and `/god`. Receiving CombatTag disables flight, and CombatTag exposes remaining combat time to the player. Do not promise a fixed tag duration without current evidence. Server/game-mode safety logic can disable perks in contexts such as combat or dungeon gameplay.