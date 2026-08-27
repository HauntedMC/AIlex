# AIlex Docs

This folder contains the current operating, architecture, testing and contribution guides for AIlex.

## Server operators

- [Configuration](CONFIGURATION.md) — adaptive models, retrieval, typed bounded read tools, memory backends, proactive intervention and diagnostics.
- [Architecture](ARCHITECTURE.md) — deterministic-first cognition, working/durable memory, temporal truth, hybrid retrieval, evidence packets and the read-only capability boundary.
- [Chat intelligence evaluation](CHAT_EVALUATION.md) — player-facing correctness, grounding, retrieval, continuity, exact identifiers, naturalness, latency, cost and liveness metrics.

Useful production checks:

```text
/ailex ai status
/ailex ai usage
/ailex memory status
/ailex trace recent
```

A representative operational test should include direct conversation, sustained multi-turn follow-ups that overflow the recent-turn window, safe live state, a custom feature-state question, exact HauntedMC knowledge, canonical identifier existence/negative questions, a semantic paraphrase, open-ended discovery, remembered/corrected/forgotten information, historical memory recall, and proactive public chat while AIlex stays out of player-to-player conversation.

For multi-runtime deployments, also verify shared-memory propagation, corrections and tombstones across two servers. If MySQL is configured, `shared_memory=false` is an operational fault rather than a second authoritative local identity.

## Contributors

- [Development](DEVELOPMENT.md) — architecture rules and day-to-day workflow.
- [Testing](TESTING.md) — deterministic regressions, neural retrieval, memory/grounding/tool/social tests, JaCoCo floors and production smoke checks.
- [Chat intelligence evaluation](CHAT_EVALUATION.md) — the separate live-model/offline quality suite that deterministic CI cannot replace.
- [Contributing Guide](../CONTRIBUTING.md) — pull request expectations.

The root [README](../README.md) contains the research/inspiration table. Only work with a concrete implementation or evaluation counterpart in AIlex should be listed there; papers that were merely read are intentionally omitted.

## Validation flow

1. Run the full build and inspect CI status.
2. Confirm `AIlexBench` and focused cognitive/liveness regressions pass.
3. Confirm the JaCoCo line/branch regression floors pass in the PR workflow.
4. Run the live-model chat evaluation for model, prompt, retrieval or context changes.
5. Confirm configuration, architecture, testing and research documentation match the current behavior.
6. Smoke-test embeddings/read-agent behavior, canonical identifier grounding and, when enabled, shared MySQL memory in a disposable environment.
7. Verify the produced plugin artifact in a representative Paper runtime.
