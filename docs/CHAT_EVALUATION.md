# AIlex chat evaluation

Player-facing quality is an evaluated product property, not something inferred from architectural complexity. Deterministic CI remains the safety net; the local benchmark runner measures stochastic provider behavior without putting paid model calls in GitHub Actions.

## Run it

```bash
export OPENAI_API_KEY="..."
./bench doctor
./bench run smoke
./bench run haunted
./bench run standard
```

Use `./bench estimate <suite>` before large runs, `./bench compare` for paired candidate comparisons, and `./bench view` for the generated local report. Full setup, external benchmark provenance and evaluator commands are documented in [`benchmark/README.md`](../benchmark/README.md).

## Why separate evaluation layers

A unit test can prove that a request was routed, a source ID was checked or a correction superseded an older value. It cannot prove that a real model response is natural, complete, helpful or robust across a sustained conversation. Conversely, a model judge can prefer a fluent answer while missing a privacy or provenance violation. AIlex therefore keeps deterministic invariants and model-facing quality evaluation separate.

The benchmark runner also keeps **published benchmark metrics separate from HauntedMC product metrics**. A LongMemEval score, an exact-identifier hard gate and a latency percentile measure different things; they are not averaged into an invented overall intelligence score.

## Published evaluation reused by AIlex

The executable benchmark system reuses established work where the capability matches:

- [LongMemEval (Wu et al., ICLR 2025)](https://proceedings.iclr.cc/paper_files/paper/2025/hash/d813d324dbf0598bbdc9c8e79740ed01-Abstract-Conference.html) for information extraction, multi-session reasoning, temporal reasoning, updates and abstention. AIlex can emit the official hypothesis format and invoke the upstream scorer.
- [MemoryAgentBench (Hu et al., ICLR 2026)](https://arxiv.org/abs/2507.05257) for accurate retrieval, test-time learning, long-range understanding and conflict resolution.
- [RAGChecker (Ru et al., NeurIPS 2024)](https://proceedings.neurips.cc/paper_files/paper/2024/hash/27245589131d17368cccdfa990cbf16e-Abstract-Datasets_and_Benchmarks_Track.html) for fine-grained retriever/generator diagnostics.
- [Inspect AI](https://inspect.aisi.org.uk/) as an optional standard evaluation/log-viewing layer over saved AIlex outputs.
- [LongMemEval-V2](https://github.com/xiaowu0162/LongMemEval-V2) as an advanced trajectory-memory benchmark. Its own multimodal `Memory` contract and accuracy/latency protocol must be respected rather than approximated with an unrelated home-grown score.

Published histories are adapted through an explicit benchmark ingestion boundary. Result metadata records that adapter protocol. External scores must never be presented as if AIlex used an upstream baseline implementation that it did not actually run.

## HauntedMC product suite

No public benchmark knows HauntedMC's current commands, Discord channels, ranks, game modes, live Bukkit state, privacy boundaries or social non-interruption requirements. These therefore need product-specific cases.

The benchmark minimizes duplicated truth. Exact-identifier cases are generated from `knowledge/entities.tsv`; authored cases cover behavior rather than copying server facts into a second database. A private gitignored holdout and sanitized real production replays can supplement the committed suite.

Useful product categories include:

| Category | What it tests |
| --- | --- |
| Natural conversation and follow-ups | continuity, pronouns, corrections, tone, useful answer length |
| HauntedMC reviewed knowledge | retrieval recall, factual correctness, exact identifiers, negative/existence questions |
| Long-term player memory | formation, correct recall, scope, relevance, selective use, privacy |
| Updates and temporal questions | current vs historical truth, correction handling, stale-data avoidance |
| Abstention and missing evidence | no hallucinated channels, commands, prices or features |
| Live Minecraft state | requester/world/NPC/server evidence and stale-snapshot resistance |
| Multi-source questions | combining memory, knowledge and live state without provenance mixing |
| Multilingual style | Dutch/English/German consistency and exact-name preservation |

Cases should include both short conversations and sustained sessions long enough to exercise recent-turn rollover and mid-term dialogue state.

## Metrics

Do not collapse everything into one score. Track at least:

1. **Answer correctness** — whether the response answers the actual question correctly.
2. **Claim faithfulness** — whether factual claims are supported by allowed evidence.
3. **Retrieval recall** — whether evidence required for the answer reached the generator.
4. **Context precision** — how much supplied context was actually relevant.
5. **Exact-identifier accuracy** — commands, channels, ranks, currencies, warps, URLs and names are copied exactly.
6. **Abstention quality** — unsupported questions do not become confident inventions.
7. **Conversation continuity** — references and corrections resolve to intended context.
8. **Memory update accuracy** — current corrected truth is used while historical truth remains queryable where appropriate.
9. **Naturalness/completeness** — useful community-member style without forced one-sentence compression or unnecessary essays.
10. **Operational cost** — provider calls and token/cache usage.
11. **Latency** — p50, p95 and fallback/timeout behavior.
12. **Liveness** — direct valid requests reach a traceable terminal outcome.

Deterministic scoring is preferred for exact identifiers, required evidence, privacy and exact-match benchmark metrics. Semantic model judging is reserved for qualities that actually require semantic interpretation.

## Quality gates

For a production candidate, run the same materialized case set against a fixed knowledge snapshot and an agreed baseline. Recommended hard gates are:

- zero silent explicit-request failures;
- zero unsupported exact HauntedMC identifiers in the reviewed identifier subset;
- all deterministic privacy/scope tests pass;
- no meaningful drop in factual correctness or claim faithfulness;
- retrieval recall is not traded away merely to reduce prompt size;
- p95 latency and average provider calls remain within the operational envelope.

Naturalness may use blind pairwise comparison plus human review of a stratified sample. An LLM judge accelerates review but is not ground truth.

## Diagnostics for a failed case

Classify a failure before changing prompts:

1. **Routing failure** — wrong intent/context family selected.
2. **Retrieval failure** — required evidence never reached the generator.
3. **Selection/context failure** — evidence was retrieved but discarded, clipped or buried.
4. **Generation failure** — evidence was present but the model reasoned or phrased incorrectly.
5. **Grounding failure** — evidence/output validation behaved incorrectly.
6. **Memory failure** — wrong scope, stale truth, missed correction or irrelevant personalization.
7. **Delivery/liveness failure** — a usable generation never reached the player.

The saved benchmark trace includes enough routing, retrieval, evidence, usage and latency information to make this taxonomy useful. It prevents the common mistake of changing the model or increasing context size when the defect is elsewhere in the pipeline.
