# AIlex Docs

This folder is the practical guide for running, upgrading, maintaining and contributing to AIlex.

## Start here

For server operators:

- [AIlex 1.5 Migration](MIGRATION-1.5.md) — required reading when upgrading from 1.4.x.
- [Configuration](CONFIGURATION.md) — production settings, Memory V2, model/token budgets and diagnostics.
- [Architecture](ARCHITECTURE.md) — request reliability, conversation state, context planning, retrieval, memory and inference flow.

For contributors:

- [Development](DEVELOPMENT.md) — local setup and day-to-day workflow.
- [Testing](TESTING.md) — test strategy and local validation commands.
- [Contributing Guide](../CONTRIBUTING.md) — pull request expectations.

## AIlex 1.5 operational checks

After upgrading, verify:

```text
/ailex ai status
/ailex ai usage
/ailex memory status
/ailex trace recent
```

Then test one direct NPC question, a short follow-up without repeating the NPC name, one current-state question and one server-fact question.

## Release flow

Releases are tag-driven:

1. Ensure lint and tests are green on the target branch.
2. Confirm migration/config documentation matches the shipped defaults.
3. Bump/tag the release using the project release workflow.
4. Push branch + tag and monitor the release workflow.
