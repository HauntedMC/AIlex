# AIlex knowledge base

Keep each item concise, factual, dated where it can change, and sourced from an approved HauntedMC document or live system.

For larger articles, use front matter so AIlex can retrieve and audit a source precisely:

```md
---
id: survival.claims
title: Survival claims
aliases: [/claim, claims, protect build]
category: server-fact
authority: official
updated: 2026-08-25
expires: null
---
Use /claim to protect a Survival build.
```

`expires` is optional. Set it for temporary events, rotations, prices, or other facts which must not be used after a date.

- Do not add passwords, API keys, player reports, ticket contents, IP addresses, staff-only procedures, sanctions, or any other personal or confidential data. Everything in this directory may be sent to the language model.
- Use one topic per bullet and include the player-facing command or official support route where useful.
- Remove or update time-sensitive facts (events, prices, rotations, queues, staff lists, availability) as soon as they change.
