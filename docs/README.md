# AIlex Docs

This folder contains the practical guides for running, configuring, maintaining and contributing to AIlex.

## Server operators

- [Configuration](CONFIGURATION.md) — assistant models, context budgets, live-data capabilities, knowledge, memory, proactive chat and diagnostics.
- [Architecture](ARCHITECTURE.md) — request reliability, dialogue state, context planning, retrieval, memory, live integrations and inference flow.

Useful production checks:

```text
/ailex ai status
/ailex ai usage
/ailex memory status
/ailex trace recent
```

A basic operational test should cover a direct NPC question, a multi-turn follow-up, a live-state question such as the current biome or held item, a server-knowledge question, an open-ended fact request, a remembered preference/correction, and a proactive public question while ensuring AIlex stays out of a player-to-player conversation.

## Contributors

- [Development](DEVELOPMENT.md) — local setup and day-to-day workflow.
- [Testing](TESTING.md) — test strategy and local validation commands.
- [Contributing Guide](../CONTRIBUTING.md) — pull request expectations.

## Release flow

1. Ensure lint and tests are green on the target branch.
2. Confirm configuration and architecture documentation match the shipped behavior.
3. Create the release using the project release workflow.
4. Monitor the release workflow and verify the produced artifact.
