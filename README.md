# AIlex

[![CI Lint](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml)
[![CI Tests and Coverage](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/AIlex?sort=semver)](https://github.com/HauntedMC/AIlex/releases/latest)
[![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/AIlex)](LICENSE)

AIlex is HauntedMC's Paper-native AI NPC and server-assistant framework. It combines deterministic Minecraft runtime logic with bounded LLM inference, active dialogue state, selective live Paper context, reviewed server knowledge, typed durable memory, and production diagnostics.

## AIlex 1.5

AIlex 1.5 replaces the original monolithic chat path with a layered assistant runtime designed for reliability, low prompt cost, and inspectability:

- thin Paper chat adapter with explicit controller/runtime boundaries;
- direct-request priority, bounded queueing, supersession, visible failure paths, and request traces;
- active player↔NPC conversations and short follow-ups without repeated mentions;
- adaptive GPT-5.6 Luna / Terra / Sol model profiles;
- hard route-specific input/output budgets and selective structured output;
- local hybrid knowledge retrieval for HauntedMC facts;
- selective read-only live player/world/server/nearby/NPC context;
- SQLite/WAL Memory V2 with typed scopes, provenance, expiry, supersession, and selected event memory;
- provider token/cache accounting and admin diagnostics;
- proactive chat that always yields capacity to direct player requests.

The LLM path is read-only. Model output cannot execute commands, mutate the world, change economy/moderation state, or directly control NPC behaviour.

## Quick Start

1. Place `AIlex.jar` in the Paper server `plugins/` directory.
2. Install `Citizens` and `packetevents`.
3. Start the server once to create the default files.
4. Configure `openai.api_key` and the assistant/chat settings in `config.yml`.
5. Create/configure NPCs with `/ailex`, or disable physical NPCs and enable the standalone assistant under `openai.chat.standalone`.
6. Use `/ailex ai status`, `/ailex ai usage`, `/ailex trace recent`, and `/ailex memory status` for production diagnostics.

The default `ailex.chat` permission is available to players; restrict `openai.chat.access_permission` or override the permission if a deployment should be staff-only.

## Requirements

- Java 25
- Paper 26.2+
- Citizens (AIlex is currently built against 2.0.43-SNAPSHOT)
- packetevents (AIlex is currently built against 2.13.0)

SQLite support for Memory V2 is embedded in `AIlex.jar`; no separate JDBC plugin is required.

## Build From Source

```bash
./gradlew clean build
```

Output jar: `build/libs/AIlex.jar`

## Version Bump Workflow

Use the helper script to bump semver, commit, and tag:

```bash
scripts/bump-version.sh patch
scripts/bump-version.sh minor --push
```

Options:

- `major|minor|patch`: required bump type
- `--push`: push branch + tag after creating them
- `--remote <name>`: push/check against a remote (default: `origin`)

## Learn More

- [Configuration Guide](docs/CONFIGURATION.md)
- [Migration to 1.5](docs/MIGRATION-1.5.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Testing and Quality](docs/TESTING.md)
- [Development Notes](docs/DEVELOPMENT.md)
- [Documentation Index](docs/README.md)
- [Contributing](CONTRIBUTING.md)

## Community

- [Support](SUPPORT.md)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
