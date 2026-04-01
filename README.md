# AIlex

[![CI Lint](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-lint.yml)
[![CI Tests and Coverage](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/AIlex/actions/workflows/ci-tests-and-coverage.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/AIlex?sort=semver)](https://github.com/HauntedMC/AIlex/releases/latest)
[![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/AIlex)](LICENSE)

AI-powered NPC framework for Paper servers, focused on movement behaviours, action pipelines, and optional LLM chat interactions.

## Release Status

AIlex is currently in a very early staged release. Core building blocks are available, but many planned functionalities are still missing and will be added in upcoming releases.

## Quick Start

1. Place `AIlex.jar` in your Paper server `plugins/` directory.
2. Install required dependencies (`Citizens`, `packetevents`).
3. Start the server once to generate default plugin files.
4. Set AIlex options in `config.yml` (especially `openai.api_key` and `openai.model` if LLM chat is used).
5. Use `/ailex` subcommands to create and control AI NPCs.

## Requirements

- Java 21
- Paper 1.21.11+
- Citizens 2.0.41+ (`citizensapi`/`citizens-main`)
- packetevents 2.11.2+

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
- [Documentation Index](docs/README.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Development Notes](docs/DEVELOPMENT.md)
- [Testing and Quality](docs/TESTING.md)
- [Contributing](CONTRIBUTING.md)

## Community

- [Support](SUPPORT.md)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
