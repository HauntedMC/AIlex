# AIlex

[![CI Lint](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml)
[![CI Tests and Coverage](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/AIlex?sort=semver)](https://github.com/HauntedMC/AIlex/releases/latest)
[![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/AIlex)](LICENSE)

AIlex is HauntedMC's Paper-native AI NPC and server-assistant framework. It combines deterministic Minecraft runtime logic with adaptive LLM inference, multi-turn dialogue, safe live Paper state, reviewed server knowledge, durable typed memory, proactive community participation and production diagnostics.

## Capabilities

- Direct player requests take priority over proactive work, with bounded concurrency, queueing and request tracing.
- Active player↔NPC conversations retain a compact multi-turn working context so natural follow-ups do not need repeated mentions.
- Adaptive GPT-5.6 Luna / Terra / Sol profiles keep ordinary chat fast while grounded and difficult questions get stronger reasoning.
- Prompt assembly uses large route-specific ceilings while selecting only useful context instead of dumping all available data.
- Local hybrid retrieval combines lexical, phrase, concept and compact dense signals for reviewed HauntedMC knowledge.
- Open-ended knowledge discovery can surface diverse server facts even when the player's question has few useful search terms.
- Read-only live context can include the requester's safe player state, inventory summary, world/biome, target, nearby entity composition, server health and NPC state.
- A public read-only context-provider API lets HauntedMC plugins expose selected safe feature state such as ranks, currencies or feature toggles without granting model-controlled plugin access.
- Durable memory separates facts, preferences, opinions, interests, goals, relationships and episodic events. Semantic keys support correction, supersession and explicit forgetting.
- Memory retrieval combines relevance, salience, confidence, type-aware recency and lightweight associative expansion while suppressing redundant results.
- Proactive questions are limited to plausible public/general questions and are suppressed during detected player-to-player conversations unless a player explicitly broadcasts the question.
- SQLite/WAL persistence, token/cache accounting and admin diagnostics support production operation.

The LLM path is read-only. Model output cannot execute commands, mutate the world, change economy/moderation state or directly control NPC behaviour. AIlex does not expose IP addresses, credentials, hidden staff data, plugin configuration or infrastructure internals to the model.

## Quick Start

1. Place `AIlex.jar` in the Paper server `plugins/` directory.
2. Install `Citizens` and `packetevents`.
3. Start the server once to create the default files.
4. Configure `openai.api_key` and the assistant/chat settings in `config.yml`.
5. Create/configure NPCs with `/ailex`, or disable physical NPCs and enable the standalone assistant under `openai.chat.standalone`.
6. Use `/ailex ai status`, `/ailex ai usage`, `/ailex trace recent`, and `/ailex memory status` for production diagnostics.

The default `ailex.chat` permission is available to players; restrict `openai.chat.access_permission` if a deployment should be limited.

## Requirements

- Java 25
- Paper 26.2+
- Citizens
- packetevents

SQLite support is embedded in `AIlex.jar`; no separate JDBC plugin is required.

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
