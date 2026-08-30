---
id: hauntedmc.chat-features
title: Chat mentions and previews
aliases: [chat mention, @player, [item], [inv], /chatplaceholders, command suggestion]
category: chat
authority: implementation-confirmed
updated: 2026-08-29
source: ServerFeatures 3.7.1: docs/features/chatlayout.md
---

Type `@ExactPlayerName` in chat to mention an online player with that exact Minecraft name. Mentions can have a cooldown shared for the mentioned player, so repeated mentions of one player may not all notify them.

Type `[item]` to share an item preview and `[inv]` to share an inventory snapshot where those previews are enabled. Bracketed commands such as `[/spawn]` can become clickable command suggestions. Use `/chatplaceholders` to see which chat additions are currently enabled for you.
