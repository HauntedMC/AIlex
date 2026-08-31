---
id: hauntedmc.player-language
title: Language preference
aliases: [/language, /taal, language, taal, dutch, english]
category: preferences
authority: implementation-confirmed
updated: 2026-08-31
source: Current ProxyFeatures command implementation: features/playerlanguage/command/LanguageCommand.java
---

Use `/language` to view your language setting. Set it with `/language AUTO`, `/language NL`, or `/language EN`. `/taal` is the registered alias; `/lang` is not a current command alias.

`NL` always uses Dutch and `EN` always uses English. `AUTO` uses available country information to choose Dutch for configured Dutch-speaking locations and English otherwise. It does not read your Minecraft client language, so choose `NL` or `EN` if you want a fixed result.
