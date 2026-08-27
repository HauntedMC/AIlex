# AIlex knowledge base

AIlex ships with reviewed HauntedMC guides for core server topics. Every managed file listed in `index.txt` is refreshed
from the plugin JAR on startup so the runtime uses the bundled reviewed source of truth. Add your own durable local
documentation as separate Markdown files; files not listed in `index.txt` are never overwritten by the built-in knowledge
synchronizer.

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

## Canonical identifiers

`entities.tsv` is the reviewed source of truth for exact HauntedMC identifiers such as Discord channels, public ranks,
active game-mode names and known commands. The plugin deterministically renders it to
`canonical-identifiers.generated.md` before the knowledge index starts. Do not edit the generated Markdown file.

The TSV format is:

```text
kind<TAB>canonical<TAB>comma-separated query aliases<TAB>description
```

A line such as `@complete<TAB>discord-channel` declares that the listed identifiers for that kind are exhaustive. AIlex may
therefore use absence from that reviewed set as negative evidence. Do this only when the operator-maintained set really is
complete. Commands are deliberately not marked complete because HauntedMC has more commands than the small player-help set
included here.

Aliases are retrieval phrases, not alternate identifiers. For example, the Dutch word `aankondigingen` may retrieve the
canonical Discord entry `#announcements`, but `#aankondigingen` itself is not thereby made into a valid channel name.

- Do not add passwords, API keys, player reports, ticket contents, IP addresses, staff-only procedures, sanctions, or any other personal or confidential data. Everything in this directory may be sent to the language model.
- Use one topic per bullet and include the player-facing command or official support route where useful.
- Treat exact commands, Discord channels, URLs, ranks, roles, warps and menu names as identifiers: store the canonical spelling and do not add translated aliases as if they were real identifiers.
- Remove or update time-sensitive facts (events, prices, rotations, queues, staff lists, availability) as soon as they change.
