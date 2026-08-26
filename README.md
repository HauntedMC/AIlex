# AIlex

[![CI Lint](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml)
[![CI Tests and Coverage](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/AIlex?sort=semver)](https://github.com/HauntedMC/AIlex/releases/latest)
[![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/AIlex)](LICENSE)

AIlex is HauntedMC's Paper-native cognitive NPC and server-assistant framework. It combines deterministic Minecraft runtime logic with adaptive LLM inference, neural hybrid retrieval, temporal lifelong memory, bounded typed read tools, claim-level grounding, multi-turn dialogue and community-aware proactive participation.

## Capabilities

- Direct player requests take priority over proactive work through bounded concurrency, queueing, supersession and request tracing.
- Active player↔NPC conversations retain compact working context so natural follow-ups do not require repeated mentions or full transcript replay.
- Adaptive GPT-5.6 Luna / Terra / Sol profiles keep routine turns fast while factual or difficult turns receive stronger grounding and reasoning.
- Context assembly uses route-specific ceilings and source budgets rather than dumping all available server, memory and conversation state into every prompt.
- Reviewed HauntedMC knowledge uses hybrid retrieval: BM25/exact/phrase/concept signals are fused with learned OpenAI semantic embeddings and reciprocal-rank fusion, then diversity-filtered.
- Corpus embeddings warm asynchronously; unavailable embeddings fall back to lexical retrieval, so the first player request never has to embed the entire knowledge base.
- `AssistantToolRegistry` exposes only explicitly registered read capabilities for memory, temporal history, verified experience, reviewed knowledge and frozen live state. The model cannot discover arbitrary plugin APIs or write tools.
- The bounded read-agent uses those tools only when deterministic evidence is insufficient and stays within model-call, tool-call and deadline budgets. Ordinary vanilla gameplay does not spend a planner call.
- Read-only live context can include safe requester state, inventory summary, world/biome, target, nearby entity composition, server health and NPC state. Permitted state is frozen on the Paper thread before asynchronous reasoning.
- A public `AssistantContextProvider` API lets trusted HauntedMC plugins expose selected player-safe feature state such as ranks, currencies or feature toggles without granting model-controlled plugin access.
- `MemoryRecord` remains the compact durable storage envelope while the cognitive layer separates `MemoryClaim`, `MemoryEvent`, `MemoryEpisode`, `MemoryEdge` and verified procedural experience.
- Stable semantic keys support correction, supersession and explicit forgetting. Corrected values receive new validity intervals instead of rewriting history.
- `MemoryTruthResolver` resolves current or historical claims deterministically from validity, source authority, confidence, salience, scope and recency. Near-tied conflicts remain disputed.
- Optional shared MySQL memory provides one network-wide durable identity with off-thread, monotonic-sequence synchronization and per-runtime hot caches; SQLite/WAL remains the self-contained local backend.
- If explicitly configured shared MySQL is unavailable at startup, AIlex uses a non-persistent fail-safe rather than silently creating divergent per-server SQLite identities.
- `EvidencePacket` and line-level `claim_evidence` keep grounded answers fail-closed. Unknown evidence IDs, partial grounding and negative-only lookup evidence cannot validate positive factual claims.
- Verified procedural experience is stored separately from player identity and can influence strategy only after deterministic/external evidence confirms the outcome.
- `SocialConversationGraph` is the single non-persistent conversation/intervention model, combining a small volatile speaker window with decaying pair edges so AIlex can stay out of likely player-to-player dialogue.
- `AIlexBench` plus focused regression suites cover routing, semantic retrieval, temporal memory, typed tools, grounding, privacy, shared synchronization and intervention behavior.
- CI enforces JaCoCo regression floors alongside the test suite. Token/cache accounting, planner/tool usage, circuit breakers, diagnostics and bounded deadlines support production operation.

The model-facing capability boundary is read-only. Model output cannot execute commands, mutate the world, change economy or moderation state, alter plugin configuration, or directly control NPC behaviour. Typed memory writes are separately validated against source evidence and privacy rules. AIlex does not expose IP addresses, credentials, hidden staff data, reports or infrastructure internals to the model.

## Quick Start

1. Place `AIlex.jar` in the Paper server `plugins/` directory.
2. Install `Citizens` and `packetevents`.
3. Start the server once to create the current default files.
4. Configure `openai.api_key` and the assistant/chat settings in `config.yml`.
5. Keep `openai.assistant.memory.storage.backend: sqlite` for a single runtime, or configure MySQL when several AIlex runtimes should share one durable memory identity.
6. Create/configure NPCs with `/ailex`, or disable physical NPCs and enable the standalone assistant under `openai.chat.standalone`.
7. Use `/ailex ai status`, `/ailex ai usage`, `/ailex trace recent`, and `/ailex memory status` for production diagnostics.

The default `ailex.chat` permission is available to players; restrict `openai.chat.access_permission` if a deployment should be limited.

## Requirements

- Java 25
- Paper 26.2+
- Citizens
- packetevents

SQLite and MySQL JDBC support are embedded in `AIlex.jar`; no separate JDBC plugin is required.

## Build From Source

```bash
./gradlew clean build
```

Output jar: `build/libs/AIlex.jar`

## Learn More

- [Configuration Guide](docs/CONFIGURATION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Testing and Quality](docs/TESTING.md)
- [Development Notes](docs/DEVELOPMENT.md)
- [Documentation Index](docs/README.md)
- [Contributing](CONTRIBUTING.md)

## Community

- [Support](SUPPORT.md)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
