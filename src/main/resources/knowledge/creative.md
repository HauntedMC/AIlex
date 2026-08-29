---
id: hauntedmc.creative
title: HauntedMC Creative plots, building and WorldEdit
aliases: [creative, plots, plot, worldedit, //wand, build, city, roleplay, plot limit, worldedit limit]
category: gamemode
authority: operator-confirmed
updated: 2026-08-29
expires: null
source: operator-provided HauntedMC Creative rank/functions documentation 2026-08-29
---

## Plot basics
Creative is HauntedMC's current building mode. Documented plot commands include `/plot claim`, `/plot auto`, `/plot home`, `/plot visit`, `/plot tp`, `/plot list`, `/plot info`, `/plot sethome`, `/plot delete`, `/plot clear` and `/plot help`.

Deleting or clearing a plot can destroy work. Explain destructive commands before suggesting them.

## Plot access and customization
Access commands include `/plot trust <name>`, `/plot add <name>`, `/plot remove <name>`, `/plot deny <name>` and `/plot kick <name>`.

Customization includes `/plot setbiome`, `/plot setdescription`, `/plot flag`, `/plot music`, `/plot middle`, `/plot rate`, `/plot comment`, `/plot inbox`, `/plot target` and `/plot toggle titles`.

## Current documented plot limits
The latest operator-provided public rank documentation gives:
- `Speler`: 2 plots;
- `Elite`: 6 plots;
- `Legend`: 8 plots;
- `Supreme`: 20 plots.

Plot merging is available from `Legend`, with a documented maximum of 4 plots per merged cluster. Merge routes include `/plot merge auto`, `/plot merge all`, directional `/plot merge ...`, road-removing merges and `/plot unlink`.

No separate Supreme+ plot count is stated in the supplied table; do not invent one if `/ranks` or the live Store differs.

## WorldEdit
WorldEdit is available from `Elite`. The current documented per-action block limits are:
- `Elite`: 50,000 blocks;
- `Legend`: 100,000 blocks;
- `Supreme`: 500,000 blocks;
- `Supreme+`: 1,000,000 blocks.

Common operations include selection (`//wand`, `//pos1`, `//pos2`), editing (`//set`, `//replace`, `//cut`, `//copy`, `//paste`, `//move`, `//stack`), history (`//undo`, `//redo`) and shapes/brushes. Legend and Supreme add broader generation/biome/advanced-brush capabilities. Do not burden ordinary answers with the full WorldEdit command catalogue unless the player asks for it specifically.

## Other Creative utilities
Creative documents normal home/economy/teleport/pose utilities, personal warps for higher ranks and `/warp city` for Haunted City. Use `/warps` and `/ranks` for the live current set rather than assuming every older named warp remains enabled.
