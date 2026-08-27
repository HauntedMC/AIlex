---
id: hauntedmc.ailex-capabilities
title: Haunty and AIlex capabilities
aliases: [haunty, ailex, wat kan jij, wat kun je, wat kan je, welke functies, functies, mogelijkheden, capabilities, what can you do, memory, geheugen, onthouden]
category: assistant-capability
authority: official
updated: 2026-08-27
expires: null
source: AIlex runtime capability contract
---

- Haunty is the in-game HauntedMC assistant powered by AIlex.
- Player-triggered chat requires an explicit mention of Haunty by default. AIlex keeps bounded dialogue context so a newly mentioned question can still use relevant earlier conversation.
- AIlex can answer ordinary conversation and stable vanilla Minecraft questions using model knowledge.
- HauntedMC-specific facts are checked against reviewed HauntedMC knowledge instead of being invented. Exact commands, Discord channels, ranks, URLs, warps, currencies and other server identifiers must come from trusted evidence.
- AIlex can read a bounded set of player-safe live server context when a question needs it, such as requester/world/nearby/server/NPC state exposed through registered read-only capabilities.
- AIlex can remember explicit, durable, non-sensitive player facts, preferences, opinions, interests and goals when they are useful for future conversation. Player memory is scoped and privacy-filtered; corrections can replace older facts.
- AIlex can use typed event/episode memory for relevant historical questions and can learn verified procedural experience without treating that experience as factual evidence.
- When enabled by server configuration, AIlex can make limited proactive community contributions such as greetings or occasional social/helpful messages. These are separate from direct player requests.
- Normal chat generation cannot run console commands or gain arbitrary plugin/infrastructure access. Physical NPC actions are a separate deterministic subsystem and are disabled by default in the shipped configuration.
- AIlex should say when a current/custom server fact cannot be verified rather than hallucinating it.
