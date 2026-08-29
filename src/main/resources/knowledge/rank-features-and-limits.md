---
id: hauntedmc.rank-features-limits
title: Current rank features and documented capacity limits
aliases: [rank perks, rank features, homes, claimblocks, player shops, backpack, plot limit, worldedit limit, elite perks, legend perks, supreme perks, supreme+ perks]
category: server-feature
authority: operator-confirmed
updated: 2026-08-29
expires: null
source: operator-provided HauntedMC global, Survival and Creative rank/functions documentation 2026-08-29
---

## How to read rank requirements
Rank requirements are cumulative: a feature marked for `Elite` or higher is intended for that tier and higher, similarly for `Legend` and `Supreme`. `Supreme+` is the highest current progression rank. `/ranks` and live permissions override this reference if the server configuration changes later.

## Global rank-gated features
Current global public documentation includes:
- `/skin <name>` and `/skin remove` from `Elite`;
- `/nickname <nick>` and `/nickname remove` from `Elite`;
- joining otherwise full servers from `Elite`;
- `/ping` from `Elite`;
- colored chat and the special join-message feature at `Supreme+`.

General social, information, linking, report, cosmetics, server-navigation and statistics commands are available independently of these premium rank gates unless a specific command says otherwise.

## Lobby poses and movement
Current Lobby documentation includes sitting on blocks for everyone and sitting on another player from `Elite`. `/lay`, `/bellyflop` and `/crawl` are documented from `Elite`; `/spin` from `Legend`; `/fly` from `Legend`. Safe-fly-login behavior is also documented for `Legend` and higher.

## Survival home limits
The supplied current public table documents:
- `Speler`: 2 homes;
- `Elite`: 10 homes;
- `Legend`: 15 homes;
- `Supreme`: 40 homes.

No separate Supreme+ home count is stated in that table; do not invent one.

## Survival online-token income
The documented online reward is paid per 20 minutes:
- `Speler`: 30 Tokens;
- `Elite`: 50 Tokens;
- `Legend`: 60 Tokens;
- `Supreme`: 100 Tokens;
- `Supreme+`: 150 Tokens.

## Survival utility perks
Selected current documented rank perks include:
- `Elite`: `/tp`, convenience workstations/utilities, item/head utilities and other basic donor conveniences;
- `Legend`: `/fly`, `/treeassist toggle`, `/autopickup`, safe-fly-login and Silk Touch spawner mining;
- `Supreme`: `/god`, `/godmacro`, `/veinminer`, `/condense`/`/blocks`, movement-speed controls and advanced convenience features.

Gameplay safety can disable a perk in combat, dungeon or restricted-world contexts.

## Survival backpacks and shop capacity
Backpack sizes:
- `Elite`: 27 slots;
- `Legend`: 36 slots;
- `Supreme`: 54 slots.

Player-shop capacity:
- `Speler`: 5 shops;
- `Elite`: 25 shops;
- `Legend`: 35 shops;
- `Supreme`: 100 shops.

## Survival claims
The supplied current public rank table documents 25 claim regions, 3,000 starting claimblocks and 40 claimblocks earned per online hour up to a 10,000 earned-block cap.

Rank bonuses are documented as:
- `Elite`: +20,000 bonus claimblocks;
- `Legend`: +40,000 bonus claimblocks;
- `Supreme`: +80,000 bonus claimblocks;
- `Supreme+`: +15,000 bonus claimblocks per month.

These are distinct from the newer compact-region spawner limits in `dynmap-and-limits.md`.

## Creative plot limits
The current supplied Creative table documents:
- `Speler`: 2 plots;
- `Elite`: 6 plots;
- `Legend`: 8 plots;
- `Supreme`: 20 plots.

Plot merging is available from `Legend`, with a maximum of 4 plots per merged cluster in the supplied documentation.

## Creative WorldEdit limits
WorldEdit is available from `Elite`. The documented maximum blocks per action are:
- `Elite`: 50,000;
- `Legend`: 100,000;
- `Supreme`: 500,000;
- `Supreme+`: 1,000,000.

Legend adds broader terrain/biome/generation tools and Supreme adds advanced brush/editing capabilities. Exact command syntax is better answered from live WorldEdit help than by reproducing the full historical command catalogue in every response.
