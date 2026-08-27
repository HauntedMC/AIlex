# AIlex

[![CI Lint](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml)
[![CI Tests and Coverage](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/AIlex?sort=semver)](https://github.com/HauntedMC/AIlex/releases/latest)
[![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/AIlex)](LICENSE)

AIlex is an AI character that lives inside a Minecraft server.

The goal is not to put a chat completion behind an NPC. AIlex is meant to behave more like a long-term staff member and community member: it can answer questions about the server, look at the Minecraft world around it, remember useful things a player has told it, notice corrections, keep conversations going across sessions, and occasionally join public chat when doing so is actually helpful.

At the same time, the language model is **not trusted with the server**. Java code decides what information it may see, what memories may be stored, whether an answer is sufficiently grounded, and whether a proposed physical action is allowed to happen.

## What AIlex feels like in game

A player should be able to talk to AIlex naturally instead of learning a special command language.

For example, a player can ask where they are, what they are holding, how a HauntedMC feature works, what AIlex remembers about them, or what they talked about previously. If the question needs current Minecraft state, AIlex can inspect a safe snapshot of the player and world. If it needs server-specific information, it searches reviewed HauntedMC documentation. If it needs history, it searches its own scoped memory.

AIlex can remember durable information such as a player's stated preferences, interests, goals and useful factual details. A later correction replaces the current belief without erasing the old historical version, so questions such as “what did I tell you before?” and “what is true now?” are different operations.

The assistant also has a small amount of physical agency. A player can explicitly ask an AIlex NPC to follow them, come over or stop moving. The model can only *propose* that action; deterministic server code independently checks the request and live state before doing anything.

AIlex may also participate without being named, but silence is a valid and often preferable choice. The proactive system tries to distinguish a public question or community moment from a conversation between players and applies cooldowns, privacy costs and repetition penalties before speaking.

## How a message becomes an answer

The main flow is intentionally simple to describe even though several systems sit underneath it:

1. **Understand the request.** Fast deterministic routing establishes the safety and capability boundary. A semantic planner can refine ambiguous information needs, but it cannot grant itself new permissions.
2. **Collect the smallest useful context.** AIlex can combine the current conversation, safe live Minecraft state, reviewed server knowledge, relevant long-term memory and verified lessons from earlier outcomes. It does not dump everything into every prompt.
3. **Look for missing evidence.** Straightforward questions use the context already available. Harder factual or historical questions can use a bounded read-only tool loop to search knowledge, memory, timelines or frozen live state more precisely.
4. **Generate and verify.** Server-specific, memory and live-state answers carry evidence IDs. Java validates that factual output is actually supported by the permitted evidence and rejects invented or mismatched sources.
5. **Learn only what is worth keeping.** Explicit, non-sensitive information can become durable memory. Verified corrections and deterministic outcomes can update beliefs or procedural experience. Model self-criticism alone is never treated as truth.

This gives AIlex a useful separation between **reasoning** and **authority**: the model is good at interpreting language and combining evidence, while code remains responsible for permissions, provenance, freshness, memory acceptance and server mutations.

## Memory that changes over time

AIlex does not use a permanent transcript as its memory.

Short-term dialogue is kept separately so a conversation can flow naturally. Long-term storage is selective and typed. The system distinguishes current claims, events, episodes, relationships and procedural experience. Memories have source information, confidence, salience and timestamps, and related memories can be reached through an associative graph as well as lexical/semantic search.

Long-lived memory is managed rather than simply accumulated. Important repeated information can mature, related events can be consolidated, weak competing memories can decay through interference-aware retention, and a memory that is successfully used with verified evidence can be reconsolidated. Retrieval by itself never makes a claim more factually true.

For multiple Minecraft servers, MySQL can act as the shared durable memory store while each runtime keeps a fast local hot view. SQLite remains available for a single-server or development setup.

## Knowing what is true

AIlex keeps different kinds of information deliberately separate:

- **Live observations** describe what the server can currently see.
- **Reviewed knowledge** contains operator-maintained HauntedMC facts and commands.
- **Player memory** contains scoped information the player explicitly supplied or that was safely recorded from interactions.
- **Shared memory** contains separately permissioned server-wide learned facts.
- **Event memory** records what happened and when.
- **Procedural experience** records verified lessons about how the assistant behaved, not facts about the world.

When values conflict over time, AIlex does not ask the LLM to guess which database row is freshest. `MemoryTruthResolver` resolves the current or historical value deterministically from validity, supersession and source/authority metadata. Close unresolved conflicts can remain disputed instead of being silently flattened into one answer.

Reviewed knowledge files support explicit metadata such as a stable ID, aliases, authority, source, update date and expiry date. That metadata is parsed and used by retrieval rather than being decorative documentation.

## Retrieval and server knowledge

HauntedMC-specific knowledge is searched with more than one signal. Exact commands and names benefit from lexical/BM25-style matching, while learned embeddings recover paraphrases. Rankings are fused, authority and freshness can affect ordering, and near-duplicate evidence is removed before it reaches the model.

If embeddings are unavailable, AIlex keeps working with lexical retrieval. Corpus embeddings warm asynchronously so a cold start does not require the first player to wait for the entire knowledge base to be embedded.

Other HauntedMC plugins can expose carefully selected player-safe facts through `AssistantContextProvider`. That is an explicit integration API; the model never receives reflection-based access to arbitrary plugin internals.

## Privacy and capability boundary

AIlex is designed around the assumption that model output can be wrong.

The model cannot directly execute server commands, run SQL, read arbitrary files, inspect arbitrary plugins, change economy or moderation state, or bypass memory/privacy validation. Other-player information is redacted by default. Secrets, credentials, IP addresses, staff-only notes, reports, sanctions and infrastructure internals are outside the model-facing data boundary.

Memory candidates are checked after generation before anything is stored. Physical NPC actions are checked again against the player's explicit request and current server state before execution.

## Research ideas used in AIlex

AIlex is an engineering system, not a reproduction of any one paper. The work below directly influenced mechanisms that are present in the codebase. Papers we read but did not translate into a concrete AIlex mechanism are intentionally not listed here.

| Research | Idea used in AIlex | Where it appears |
| --- | --- | --- |
| [Hindsight: Structured Agent Memory that Retains, Recalls, and Reflects — Latimer et al., ACL 2026](https://aclanthology.org/2026.acl-demo.27/) | Keep factual/world information, observations, opinions/claims and experience conceptually distinct; combine lexical, vector, graph and temporal memory cues. | Typed memory views, epistemic evidence classes, hybrid memory retrieval and temporal filtering. |
| [Human-Inspired Memory Architecture for LLM Agents — Kerestecioglu et al., 2026](https://www.microsoft.com/en-us/research/publication/human-inspired-memory-architecture-for-llm-agents/) | Consolidation, interference-based forgetting, maturation, reconsolidation and multi-cue/entity-graph memory. | `AssistantMemoryConsolidator`, `MemoryRetentionPolicy`, `MemoryLifecycleStage`, verified reconsolidation and graph-assisted recall. |
| [From RAG to Memory: Non-Parametric Continual Learning for Large Language Models (HippoRAG 2) — Gutiérrez et al., 2025](https://arxiv.org/abs/2502.14802) | Associative graph retrieval and Personalized-PageRank-style propagation should complement vector/lexical retrieval rather than replace factual retrieval. | `MemoryGraphRetriever` and graph-score fusion in durable-memory search. |
| [Self-RAG: Learning to Retrieve, Generate, and Critique through Self-Reflection — Asai et al., ICLR 2024](https://openreview.net/forum?id=hSyW5go0v8) | Retrieval should be adaptive rather than mandatory, and generated factual content should be checked against supporting evidence. AIlex adapts the principle without Self-RAG's trained reflection tokens. | Information-gain-gated read-agent, evidence acquisition and fail-closed claim grounding. |
| [LongMemEval: Benchmarking Chat Assistants on Long-Term Interactive Memory — Wu et al., 2024](https://arxiv.org/abs/2410.10813) | Long-term memory must be evaluated on multi-session reasoning, temporal reasoning, updates and abstention rather than fact lookup alone. | Temporal/update/abstention cases in memory and `AIlexBench` regression coverage. |
| [MemoryAgentBench: Evaluating Memory in LLM Agents via Incremental Multi-Turn Interactions — Hu, Wang & McAuley, 2025/2026](https://arxiv.org/abs/2507.05257) | Treat accurate retrieval, test-time learning, long-range understanding and selective forgetting as separate memory competencies. | Benchmark categories plus selective retention/forgetting and correction tests. |
| [Don't Ask the LLM to Track Freshness: A Deterministic Recipe for Memory Conflict Resolution — Reddy & Challaram, 2026](https://arxiv.org/abs/2606.01435) | Resolve changing values deterministically after retrieval instead of asking the language model to choose the freshest conflicting claim. | `MemoryTruthResolver`, validity intervals, supersession and historical/current-value separation. |
| [Mem2ActBench: A Benchmark for Evaluating Long-Term Memory Utilization in Task-Oriented Autonomous Agents — Shen et al., ACL 2026](https://aclanthology.org/2026.acl-long.370/) | Memory quality should include whether remembered information can safely influence tool/action behavior, not only whether it can be repeated in an answer. | Memory→tool/action regression scenarios and verified action-outcome experience. |

These are inspirations and evaluation targets, not claims of paper-equivalent implementations or benchmark scores. AIlex deliberately adapts the ideas to a deterministic Paper-server safety boundary.

## Running AIlex

### Requirements

- Java 25
- Paper 26.2+
- Citizens
- packetevents
- an OpenAI API key for model/embedding features

SQLite and MySQL JDBC support are bundled in the plugin jar.

### Installation

1. Put `AIlex.jar`, Citizens and packetevents in the Paper server's `plugins/` directory.
2. Start the server once so AIlex creates its configuration and knowledge directory.
3. Set `openai.api_key` in `config.yml`.
4. Keep `openai.assistant.memory.storage.backend: sqlite` for a single AIlex runtime, or configure MySQL when several servers should share one persistent AIlex identity.
5. Create and configure NPCs with `/ailex`. A standalone non-NPC assistant can instead be enabled under `openai.chat.standalone`.
6. Put reviewed HauntedMC documentation in the configured `knowledge/` directory and use the documented front matter for authority/freshness-sensitive information.

Useful production commands include:

```text
/ailex ai status
/ailex ai usage
/ailex memory status
/ailex trace recent
```

The default `ailex.chat` permission is available to players. Set `openai.chat.access_permission` if access should be restricted.

## Building and testing

Build everything locally with:

```bash
./gradlew clean build
```

The jar is written to `build/libs/AIlex.jar`.

For the same quality gates used by CI:

```bash
./gradlew --no-daemon checkstyleMain checkstyleTest
./gradlew --no-daemon test jacocoTestReport jacocoTestCoverageVerification
```

Model-provider calls are not required by deterministic CI. Semantic retrieval tests use deterministic fake embeddings so routing, fusion, memory, evidence, privacy and agent-control regressions remain reproducible.

## Documentation

- [Configuration guide](docs/CONFIGURATION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Testing and quality](docs/TESTING.md)
- [Development notes](docs/DEVELOPMENT.md)
- [Documentation index](docs/README.md)
- [Contributing](CONTRIBUTING.md)

## Project policies

- [Support](SUPPORT.md)
- [Security policy](SECURITY.md)
- [Code of conduct](CODE_OF_CONDUCT.md)
