---
id: hauntedmc.resource-packs
title: Resource packs
aliases: [/resourcepack, /resourcepack list, resource pack, texture pack, pack declined, pack download]
category: connection
authority: implementation-confirmed
updated: 2026-08-31
source: Current ProxyFeatures command implementation: features/resourcepack/command/ResourcePackCommand.java
---

HauntedMC can offer a global resource pack when you join and a different pack for a specific game mode. Switching to a server with its own pack can change the pack; leaving that server removes its server-specific pack.

Where the command is available to you, `/resourcepack list` shows the pack assignment the proxy currently knows for your own connection. The bare `/resourcepack` command only shows its usage. Looking up another player, reapplying a pack, and reloading packs require separate access and are not normal player commands.

If a pack is required, declining it or letting it fail to download/apply disconnects you. Optional pack failures allow the connection to continue. If a pack issue persists, reconnect first and then ask support with the exact error rather than repeatedly guessing at pack commands.
