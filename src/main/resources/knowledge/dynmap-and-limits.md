---
id: hauntedmc.dynmap-limits
title: Dynmap, maps and current server limits
aliases: [dynmap, map, maps, /maps, /dynmap hide, /dynmap show, limits, /limits, entity limits, hopper limits, spawner limits]
category: server-feature
authority: operator-confirmed
updated: 2026-08-29
expires: null
source: operator-provided HauntedMC public help + major update 2026-08-04
---

## Dynmap
HauntedMC provides browser maps for Survival and Creative:
- `https://hauntedmc.nl/maps/survival`
- `https://hauntedmc.nl/maps/creative`

The documented Dynmap can show coordinates, player location/health/rank/name, world borders, Survival claim information, Creative plot information, spawn locations and 2D/3D map views. Individual layers can still depend on the live map configuration.

Use `/dynmap hide` to hide yourself from the map and `/dynmap show` to become visible again. `/maps` is the general in-game information route.

## General base limits
The latest detailed public limits supplied by the operator document these limits where not superseded by a newer changelog:
- hoppers: maximum 2,000 in one base;
- chests and other containers: maximum 2,000 in one base;
- animals: 20 per distinguishable cluster;
- hostile/passive mobs: 15 per cluster;
- villagers: 8 per cluster;
- bees: 9 per cluster with 25 blocks free distance;
- allays: 5 per cluster with 25 blocks free distance;
- hostile mobs in mob farms: 5 per cluster, with at least 3 blocks between mob-farm clusters.

Tagged mobs and pets count toward the applicable entity limits. For base separation, the public documentation describes separate bases as areas that are not simultaneously within the same render/view distance.

## Spawner limits — newer August 2026 rule wins
The old help-page value of 100 spawners per base is superseded by the newer 4 August 2026 spawner-limit system. The current documented compact-region limits are:
- `Speler`: 2 spawners;
- `Elite`: 5 spawners;
- `Legend`: 15 spawners;
- `Supreme`: 25 spawners.

A separate absolute safety cap still applies, and the system also tracks living mobs associated with spawners. The changelog did not publish a separate Supreme+ number, so do not invent one; use `/limits` or live server state for the effective current value.

## Freshness rule
`/limits` and live game-mode configuration override this file if HauntedMC changes a numeric limit later. Never combine the superseded 100-spawner rule with the newer rank-based limits.
