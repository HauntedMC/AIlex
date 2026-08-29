---
id: hauntedmc.dynmap-limits
title: Dynmap, maps and current server limits
aliases: [dynmap, map, maps, /maps, /dynmap hide, /dynmap show, limits, /limits, entity limits, hopper limits, spawner limits]
category: server-feature
authority: operator-confirmed
updated: 2026-08-29
expires: null
source: operator-provided HauntedMC help + major update 2026-08-04
---

## Maps
Survival: `https://hauntedmc.nl/maps/survival`; Creative: `https://hauntedmc.nl/maps/creative`. Documented layers: coordinates, player location/health/rank/name, world border, Survival claims, Creative plots, spawn, 2D/3D. Live map config decides enabled layers. `/dynmap hide` hides you; `/dynmap show` restores visibility; `/maps` is the general route.

## Base/entity limits
| Item/entity | Limit |
|---|---:|
| Hoppers | 2,000/base |
| Chests + other containers | 2,000/base |
| Animals | 20/cluster |
| Hostile/passive mobs | 15/cluster |
| Villagers | 8/cluster |
| Bees | 9/cluster + 25 blocks free distance |
| Allays | 5/cluster + 25 blocks free distance |
| Hostile mobs in mob farms | 5/cluster + ≥3 blocks between farm clusters |

Tagged mobs/pets count. Separate bases require they are not simultaneously within the same render/view distance.

## Spawners — newer rule
The old 100-spawners/base value is superseded by the 4 Aug 2026 compact-region system:

| Rank | Spawners |
|---|---:|
| Speler | 2 |
| Elite | 5 |
| Legend | 15 |
| Supreme | 25 |

A separate absolute safety cap and associated-living-mob tracking also apply. No separate Supreme+ number was published. Newer `/limits`/live config overrides this snapshot.
