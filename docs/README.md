# AIlex Docs

This folder contains the current operating, architecture, testing and contribution guides for AIlex.

## Server operators

- [Configuration](CONFIGURATION.md) — adaptive models, neural retrieval, bounded read tools, memory backends, proactive intervention and diagnostics.
- [Architecture](ARCHITECTURE.md) — deterministic-first cognition, context planning, retrieval, temporal memory, experience, grounding, live integrations and research basis.

Useful production checks:

```text
/ailex ai status
/ailex ai usage
/ailex memory status
/ailex trace recent
```

A representative operational test should include: direct conversation, a multi-turn follow-up, safe live state, a custom feature-state question, exact HauntedMC knowledge, a semantic paraphrase, open-ended discovery, remembered/corrected/forgotten information, historical memory recall, and proactive public chat while AIlex stays out of player-to-player conversation.

For multi-runtime deployments, also verify shared memory propagation and tombstones across two servers.

## Contributors

- [Development](DEVELOPMENT.md) — architecture rules and day-to-day workflow.
- [Testing](TESTING.md) — AIlexBench, deterministic regressions, retrieval/memory/grounding tests and production smoke checks.
- [Contributing Guide](../CONTRIBUTING.md) — pull request expectations.

## Release flow

1. Run the full build and inspect CI status.
2. Confirm `AIlexBench` and focused cognitive regressions pass.
3. Confirm configuration and architecture docs match the shipped behavior.
4. Smoke-test embeddings/read-agent behavior and, when enabled, shared MySQL memory in a disposable environment.
5. Create the release using the project release workflow and verify the produced artifact.
