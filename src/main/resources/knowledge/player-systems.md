---
id: hauntedmc.player-systems
title: DeathChests, AutoPickup, messages, lottery and CombatTag
aliases: [deathchest, grave, graveyard, autopickup, drop2inventory, /msg mode, lottery, combattag, fly, god]
category: server-feature
authority: official
updated: 2026-08-29
expires: null
source: HauntedMC major update 2026-08-04 + current rank documentation
---

**DeathChest/Graveyard:** virtual grave at death location, showing player + remaining time; current claim time **10 min**. Stores the XP Minecraft would normally drop. Partial claim is safe: full inventory leaves remaining items protected.

**AutoPickup:** replacement for Drop2Inventory; from `Legend`, toggle `/autopickup`. Only blocks personally broken; never overwrites inventory; excess drops normally; free offhand can be used; setting persists across HauntedMC servers.

**Private messages:** `/msg mode` = `FRIENDS` or `ALL`; `FRIENDS` default for new players. Staff can still contact players; implementation avoids leaking vanished-staff presence.

**Lottery:** tickets, jackpot, donations, statistics, displayed win chance; buy via `/lottery buy`. Price/jackpot/draw time/odds/entries are live values.

**FairPerks/CombatTag:** rank-dependent `/fly` and `/god`; CombatTag disables flight. Combat, dungeon and restricted-world safety may disable perks.
