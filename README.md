# AIlex

[![CI Lint](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml)
[![CI Tests and Coverage](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/AIlex?sort=semver)](https://github.com/HauntedMC/AIlex/releases/latest)
[![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/AIlex)](LICENSE)

AIlex is an AI character that lives inside a Minecraft server.

The goal is not to put a chat completion behind an NPC. AIlex is meant to behave more like a long-term community member: it can hold natural multi-turn conversations, answer questions about HauntedMC, inspect explicitly exposed Minecraft state, remember useful player-supplied information, notice corrections, preserve continuity across sessions, and occasionally join public chat when doing so is actually helpful.

At the same time, the language model is **not trusted with the server**. Java code decides what information it may see, what memories may be stored, whether an answer is sufficiently grounded, which exact HauntedMC identifiers exist, and whether any optional physical action is allowed to happen.

## What AIlex feels like in game

A player should be able to talk to AIlex naturally instead of learning a special command language.

Ordinary conversation uses a capable player-facing model instead of the cheapest routing model, and answer length is adaptive: simple chat stays short, while an explanation can use several Minecraft-chat lines when brevity would remove useful information. Recent dialogue is sent to the Responses API with real `user`/`assistant` roles rather than being flattened into one fake user transcript.

For example, a player can ask where they are, what they are holding, how a HauntedMC feature works, whether a Discord channel really exists, what AIlex remembers about them, or what they talked about previously. If the question needs current Minecraft state, AIlex can inspect a safe snapshot of the player and world. If it needs server-specific information, it searches reviewed HauntedMC documentation and the canonical identifier registry. If it needs history, it searches its own scoped memory.

AIlex can remember durable information such as a player's stated preferences, interests, goals and useful factual details. A later correction replaces the current belief without erasing the old historical version, so questions such as “what did I tell you before?” and “what is true now?” are different operations.

Physical NPC actions are an optional bounded subsystem, disabled by default, and are not part of normal chat inference. Chat availability is independent from whether the Citizens entity is physically spawned.

AIlex may also participate without being named, but silence is a valid and often preferable choice. The proactive system tries to distinguish a public question or community moment from a conversation between players and applies cooldowns, privacy costs and repetition penalties before speaking.

## How a message becomes an answer

The main flow is intentionally simple to describe even though several systems sit underneath it:

1. **Establish the safety/capability boundary.** Deterministic routing decides what the request is allowed to access. Semantic refinement may broaden information needs inside that boundary but cannot grant itself new permissions.
2. **Resolve conversation state.** Recent turns remain verbatim. Older turns that roll out of the immediate window are folded into bounded mid-term dialogue state instead of being silently discarded or promoted to durable player memory.
3. **Collect only useful evidence.** AIlex combines the current request, real role-typed dialogue, safe live Minecraft state, reviewed server knowledge, canonical identifiers, relevant long-term memory and verified procedural experience only when those sources can materially help.
4. **Retrieve adaptively.** The knowledge index fuses lexical/BM25-style matching, exact/title/alias signals, embeddings, reciprocal-rank fusion, authority and freshness. A deterministic second stage keeps a smaller precise evidence set for clear high-confidence questions while preserving more recall for ambiguous/broad ones.
5. **Compile a bounded context.** The configured 4k/12k/24k route limits are ceilings, not targets. Raw historical context is lower priority; current trusted evidence is deliberately positioned so it is not buried inside an unnecessarily long prompt.
6. **Generate with the appropriate model.** Terra is the normal player-facing tier, Luna remains useful for cheap planning/background work, and Sol is reserved for difficult deliberate/escalation work.
7. **Verify before delivery.** Server-specific, memory and live-state factual replies carry evidence IDs. Java rejects invented/mismatched evidence and exact HauntedMC identifiers not present in trusted evidence.
8. **Learn selectively.** Explicit non-sensitive durable information may become validated long-term memory. Verified corrections and deterministic outcomes may update beliefs or procedural experience. Model self-criticism alone is never truth.

This separates **reasoning** from **authority**: models interpret language and combine evidence, while code owns permissions, identity, provenance, freshness, memory acceptance and server mutation.

## Conversation and memory over time

AIlex does not use one permanent transcript as memory.

The working conversation has two bounded layers. **Recent dialogue** keeps the newest player↔AIlex turns verbatim for pronouns, corrections and natural follow-ups. **Mid-term dialogue state** retains a compact rolling digest/topic state when older recent turns leave that window, preserving continuity without stuffing an entire session into every prompt. Neither working layer becomes durable personal memory automatically.

Long-term storage is separately selective and typed. The system distinguishes current claims, events, episodes, relationships and procedural experience. Memories have source information, confidence, salience and timestamps, and related memories can be reached through an associative graph as well as lexical/semantic search.

Long-lived memory is managed rather than simply accumulated. Important repeated information can mature, related events can be consolidated, weak competing memories can decay through interference-aware retention, and a memory that is successfully used with verified evidence can be reconsolidated. Retrieval by itself never makes a claim more factually true.

For multiple Minecraft servers, MySQL can act as the shared durable memory store while each runtime keeps a fast local hot view. SQLite remains available for a single-server or development setup.

## Knowing what is true

AIlex keeps different kinds of information deliberately separate:

- **Live observations** describe what the server can currently see.
- **Reviewed knowledge** contains operator-maintained HauntedMC facts and commands.
- **Canonical identifiers** contain audited proper names such as Discord channels, ranks, game modes and known commands.
- **Player memory** contains scoped information the player explicitly supplied or that was safely recorded from interactions.
- **Shared memory** contains separately permissioned server-wide learned facts.
- **Event memory** records what happened and when.
- **Procedural experience** records verified lessons about how the assistant behaved, not facts about the world.

When values conflict over time, AIlex does not ask the LLM to guess which database row is freshest. `MemoryTruthResolver` resolves the current or historical value deterministically from validity, supersession and source/authority metadata. Close unresolved conflicts can remain disputed instead of being silently flattened into one answer.

Reviewed knowledge files support explicit metadata such as a stable ID, aliases, authority, source, update date and expiry date. That metadata is parsed and used by retrieval rather than being decorative documentation.

### Canonical HauntedMC identifiers

`knowledge/entities.tsv` is a separate reviewed registry for identifiers whose exact spelling matters. Query aliases help retrieval but never become alternate identifiers. For example, Dutch `aankondigingen` can lead AIlex to `#announcements`; it does **not** make `#aankondigingen` a real Discord channel.

Some identifier kinds can be marked complete. For a complete kind, absence is meaningful negative evidence. The plugin deterministically renders the registry into a generated reviewed Markdown source before the knowledge index starts, so the normal grounding pipeline sees both exact names and completeness rules. Commands are intentionally not marked complete unless the operator can guarantee the full command set is represented.

## Retrieval and server knowledge

HauntedMC-specific knowledge is searched with more than one signal. Exact commands and names benefit from lexical/BM25-style matching, while learned embeddings recover paraphrases. Rankings are fused, authority and freshness can affect ordering, and near-duplicate evidence is removed before it reaches the model.

An adaptive selection stage runs after first-stage retrieval. Clear, strongly ranked questions use fewer chunks; broad or ambiguous questions retain a wider evidence set. This is deliberately deterministic rather than another mandatory LLM call. It reduces context noise while avoiding an always-on reranker that could throw away useful recall.

If embeddings are unavailable, AIlex keeps working with lexical retrieval. Corpus embeddings warm asynchronously so a cold start does not require the first player to wait for the entire knowledge base to be embedded.

Other HauntedMC plugins can expose carefully selected player-safe facts through `AssistantContextProvider`. That is an explicit integration API; the model never receives reflection-based access to arbitrary plugin internals.

## Privacy and capability boundary

AIlex is designed around the assumption that model output can be wrong.

The model cannot directly execute server commands, run SQL, read arbitrary files, inspect arbitrary plugins, change economy or moderation state, or bypass memory/privacy validation. Other-player information is redacted by default. Secrets, credentials, IP addresses, staff-only notes, reports, sanctions and infrastructure internals are outside the model-facing data boundary.

Player conversation state is application-managed and OpenAI response storage is disabled by default (`store: false`). AIlex manually supplies the bounded prior dialogue it wants the model to see rather than relying on provider-side conversation persistence.

Memory candidates are checked after generation before anything is stored. Optional physical NPC actions remain separately deterministic and disabled by default.

## Research ideas used in AIlex

AIlex is an engineering system, not a reproduction of any one paper. The work below directly influenced mechanisms that are present in the codebase. Papers we read but did not translate into a concrete AIlex mechanism are intentionally not listed here. Where a paper contains learned/RL components that AIlex does not implement, the table states only the narrower idea actually adapted.

| Research | Idea used in AIlex | Where it appears |
| --- | --- | --- |
| [Hindsight: Structured Agent Memory that Retains, Recalls, and Reflects — Latimer et al., ACL 2026](https://aclanthology.org/2026.acl-demo.27/) | Keep factual/world information, observations, opinions/claims and experience conceptually distinct; combine lexical, vector, graph and temporal memory cues. | Typed memory views, epistemic evidence classes, hybrid memory retrieval and temporal filtering. |
| [Human-Inspired Memory Architecture for LLM Agents — Kerestecioglu et al., 2026](https://www.microsoft.com/en-us/research/publication/human-inspired-memory-architecture-for-llm-agents/) | Consolidation, interference-based forgetting, maturation, reconsolidation and multi-cue/entity-graph memory. | `AssistantMemoryConsolidator`, `MemoryRetentionPolicy`, `MemoryLifecycleStage`, verified reconsolidation and graph-assisted recall. |
| [From RAG to Memory: Non-Parametric Continual Learning for Large Language Models (HippoRAG 2) — Gutiérrez et al., ICML 2025](https://proceedings.mlr.press/v267/gutierrez25a.html) | Associative graph/PPR-style retrieval should complement rather than destroy strong factual retrieval. | `MemoryGraphRetriever` plus graph-score fusion alongside lexical/semantic factual recall. |
| [Self-RAG: Learning to Retrieve, Generate, and Critique through Self-Reflection — Asai et al., ICLR 2024](https://openreview.net/forum?id=hSyW5go0v8) | Retrieval should be adaptive rather than mandatory, and generated factual content should be checked against supporting evidence. AIlex adapts the principle without Self-RAG's trained reflection tokens. | Information-gain-gated read-agent, evidence acquisition and fail-closed claim grounding. |
| [Adaptive-RAG: Learning to Adapt Retrieval-Augmented Large Language Models through Question Complexity — Jeong et al., NAACL 2024](https://aclanthology.org/2024.naacl-long.389/) | Different question complexity warrants different retrieval effort rather than one fixed expensive strategy. AIlex uses deterministic confidence/query-complexity heuristics rather than the paper's trained classifier. | `AdaptiveEvidencePolicy`, bounded read-agent/tool rounds and route-specific generation budgets. |
| [How Does Knowledge Selection Help Retrieval Augmented Generation? — Li & Ouyang, Findings of EMNLP 2025](https://aclanthology.org/2025.findings-emnlp.218/) | Strong generators on clear tasks often benefit most from recall, while selection becomes more valuable on ambiguous tasks; reranking is not universally helpful. | Adaptive second-stage evidence budget instead of an always-on LLM/cross-encoder reranker. |
| [Lost in the Middle: How Language Models Use Long Contexts — Liu et al., TACL 2024](https://aclanthology.org/2024.tacl-1.9/) | Bigger context windows do not guarantee robust use of evidence, and relevant information can be harmed by poor placement. | Bounded 4k/12k/24k ceilings, query/trust-aware context allocation, lower-priority history earlier and current trusted evidence later. |
| [In Prospect and Retrospect: Reflective Memory Management for Long-term Personalized Dialogue Agents — Tan et al., ACL 2025](https://aclanthology.org/2025.acl-long.413/) | Dialogue information benefits from multiple granularities rather than a single rigid memory unit. AIlex adapts the granularity idea, not RMM's online-RL retrieval mechanism. | Verbatim recent dialogue plus bounded mid-term digest/topic state plus separate durable memory. |
| [Memory OS of AI Agent — Kang et al., EMNLP 2025](https://aclanthology.org/2025.emnlp-main.1318/) | Separate short-, mid- and long-term memory layers with different update/retention behavior. | Recent-turn working memory, mid-term dialogue state and selective typed durable memory. |
| [LongMemEval: Benchmarking Chat Assistants on Long-Term Interactive Memory — Wu et al., ICLR 2025](https://proceedings.iclr.cc/paper_files/paper/2025/hash/d813d324dbf0598bbdc9c8e79740ed01-Abstract-Conference.html) | Long-term assistants should be evaluated separately on extraction, multi-session reasoning, temporal reasoning, updates and abstention. | Memory regression categories and `docs/CHAT_EVALUATION.md`. |
| [MemoryAgentBench: Evaluating Memory in LLM Agents via Incremental Multi-Turn Interactions — Hu, Wang & McAuley, 2025/2026](https://arxiv.org/abs/2507.05257) | Treat accurate retrieval, test-time learning, long-range understanding and selective forgetting as separate memory competencies. | Benchmark categories plus selective retention/forgetting and correction tests. |
| [RAGChecker: A Fine-grained Framework for Diagnosing Retrieval-Augmented Generation — Ru et al., NeurIPS 2024](https://proceedings.neurips.cc/paper_files/paper/2024/hash/27245589131d17368cccdfa990cbf16e-Abstract-Datasets_and_Benchmarks_Track.html) | Diagnose retrieval and generation separately instead of trusting one end-to-end score. | `docs/CHAT_EVALUATION.md`: retrieval recall/precision, claim faithfulness, exact identifiers, naturalness, latency and cost are tracked separately. |
| [Don't Ask the LLM to Track Freshness: A Deterministic Recipe for Memory Conflict Resolution — Reddy & Challaram, 2026](https://arxiv.org/abs/2606.01435) | Resolve changing values deterministically after retrieval instead of asking the language model to choose the freshest conflicting claim. | `MemoryTruthResolver`, validity intervals, supersession and historical/current-value separation. |
| [Mem2ActBench: A Benchmark for Evaluating Long-Term Memory Utilization in Task-Oriented Autonomous Agents — Shen et al., ACL 2026](https://aclanthology.org/2026.acl-long.370/) | Memory quality should include whether remembered information can safely influence tool/action behavior, not only whether it can be repeated in an answer. | Memory→tool/action regression scenarios and verified action-outcome experience; physical actions remain optional and disabled by default. |

These are inspirations and evaluation targets, not claims of paper-equivalent implementations or benchmark scores. AIlex deliberately adapts the ideas to a deterministic Paper-server safety boundary.

### Provider/API design

The role-typed multi-turn transport is based on the official [OpenAI Responses API](https://developers.openai.com/api/reference/cli/resources/responses/methods/create), which accepts message/input items and supports application-managed/stateless continuation. AIlex keeps `store: false` and replays only its bounded conversation state. This is an API integration choice, not a research-paper claim.

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
6. Put reviewed HauntedMC documentation in the configured `knowledge/` directory and use the documented front matter for authority/freshness-sensitive information. Maintain exact identifiers in `knowledge/entities.tsv`.

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

Deterministic CI does not require live model-provider calls. Semantic retrieval tests use deterministic fake embeddings so routing, fusion, memory, evidence, privacy and agent-control regressions remain reproducible. `docs/CHAT_EVALUATION.md` defines the separate live-model/offline quality suite for player-facing behavior.

## Documentation

- [Configuration guide](docs/CONFIGURATION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Testing and quality](docs/TESTING.md)
- [Chat intelligence evaluation](docs/CHAT_EVALUATION.md)
- [Development notes](docs/DEVELOPMENT.md)
- [Documentation index](docs/README.md)
- [Contributing](CONTRIBUTING.md)

## Project policies

- [Support](SUPPORT.md)
- [Security policy](SECURITY.md)
- [Code of conduct](CODE_OF_CONDUCT.md)
