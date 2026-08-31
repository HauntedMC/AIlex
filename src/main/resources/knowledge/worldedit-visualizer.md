---
id: hauntedmc.worldedit-visualizer
title: WorldEdit selection visualizer
aliases: [/worldeditvisualizer, WorldEdit outline, selection visualizer, refresh selection]
category: building
authority: implementation-confirmed
updated: 2026-08-31
source: Current ServerFeatures command implementation: features/worldeditvisualizer/command/WorldEditVisualizerCommand.java
---

Where you have access, `/worldeditvisualizer` toggles a packet-only outline of your current WorldEdit selection. It does not change blocks, regions, or your WorldEdit selection.

Use `/worldeditvisualizer on` (or `enable`), `/worldeditvisualizer off` (or `disable`), and `/worldeditvisualizer refresh` to control the display. `refresh` also enables the visualizer when it was off. `/wevis` is not a currently registered alias.
