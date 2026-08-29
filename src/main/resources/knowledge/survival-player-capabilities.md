---
id: hauntedmc.survival-player-capabilities
title: Survival player-visible systems and utilities
aliases: [backpack, backpacks, crates, crate keys, fishing, custom fish, pets, morphs, disguises, particles, voice chat, proximity voice, sleep, night skip, trade, player trade, mob money, mob rewards, npc trader, leaderboards, tool stats, pinata, piñata, shulker]
category: gamemode-feature
authority: operator-confirmed
updated: 2026-08-29
expires: null
source: operator-confirmed current Survival runtime capability inventory 2026-08-29; exact configuration remains live
---

This article records **player-visible capabilities**, not implementation/component names. Presence confirms the subsystem; exact commands, prices, thresholds, odds, locations, permissions and reward values are only stated where separately verified.

## Progression and rewards
Survival supports McMMO skill progression; known commands include `/mcstats`, `/mctop <skill>`, `/mcrank`, `/mcmmo help`, `/mcinfo`, `/party` and `/redeem`. Online/play rewards and leaderboards are also available through the documented `/rewards`, `/playtop`, `/afktop` and `/leaderboard` routes.

A crate/key reward system, expanded fishing content and mob-kill money rewards are present. Crate types/locations/odds, fish pools/rarities/events and mob reward amounts/conditions are live configuration and must not be invented. Community reward/event mechanics can include a Piñata Party when active; do not infer its trigger, schedule or rewards without current evidence.

## Inventory and convenience
Rank-dependent backpacks are available; current slot capacities are stored in the rank-capacity knowledge. Enhanced shulker-box interaction is supported, but exact interaction/command syntax is live.

Tree-felling assistance and vein-mining are supported rank perks. Verified entry points are `/treeassist toggle` from Legend and `/veinminer` from Supreme. AutoPickup is a separate documented Legend perk.

Supported tools can carry tracked usage/statistics. Treat the exact tracked counters and eligible items as live configuration unless current help describes them.

## Trading, shops and world interaction
Survival has player/chest shops, claim-aware shop integration, rentable/protected market regions and shop search; `/warp mall`, `/finditem`, `/shop history` and `/shop list` are separately verified. A direct player-to-player trade subsystem also exists, but this inventory alone does not verify its current command alias or trade restrictions; use live help/tab completion for exact syntax.

NPC trader interactions, hologram displays and menu-driven interfaces are used in player-facing areas. Their locations, inventory and options are dynamic.

A multiplayer sleep/night-skip system is active; do not invent the required sleeping-player percentage or timing because those are configuration values.

## Social, cosmetics and communication
Survival supports pets, morph/disguise-style cosmetics, particle cosmetics and player-head cosmetics/utilities. Availability can depend on rank, unlocks, rewards, event state or permissions; do not claim a specific unlock path unless separately verified.

Sitting/lying/crawling interactions are supported; known rank documentation includes `/lay`, `/bellyflop` and `/crawl` from Elite plus sitting on players.

Optional proximity voice chat support is present. Players need a compatible client-side voice-chat setup to use proximity voice; exact client/version/setup details should come from current server help rather than being guessed.

## Protection and recovery
Claims/region protection, entity/farm limits and server-side investigation/recovery tooling are active. Staff can have block/action history and inventory rollback evidence available when investigating griefing or loss, but this does **not** guarantee that every incident can or will be rolled back. Use `/support` for incidents requiring staff review.
