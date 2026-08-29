# AIlex local benchmark system

The benchmark runner measures AIlex with real provider calls without making stochastic or paid evaluation part of normal CI. It uses the production Java assistant pipeline for HauntedMC cases and adapts published memory datasets where they measure a capability AIlex actually has.

## Principles

- `OPENAI_API_KEY` comes from the local process environment and is never written to the repository or result files.
- Live benchmark execution refuses to run when `CI=true`.
- Published benchmark identities and scoring rules stay separate from HauntedMC product metrics. There is deliberately no invented single "AIlex intelligence score".
- Deterministic invariants such as exact identifiers, evidence IDs and privacy remain hard checks. Semantic model judges supplement these checks; they do not replace them.
- External benchmark data and generated history fixtures live under `benchmark/.cache/` and are not vendored into AIlex.
- `benchmark/results/` and `benchmark/local/` are gitignored. Use `benchmark/local/holdout.jsonl` and `benchmark/local/replays.jsonl` for private holdouts and sanitized production regressions.

## Quick start

```bash
export OPENAI_API_KEY="..."

./bench doctor
./bench setup
./bench selftest
./bench run smoke
./bench view latest
```

`./bench setup` creates `benchmark/.venv`, installs Inspect AI and Hugging Face Datasets, and checks out official upstream benchmark repositories under the local cache. Add `--ragchecker` to install RAGChecker and its spaCy model, `--official` to install the official LongMemEval scorer requirements, or `--v2` to create the isolated LongMemEval-V2 environment as well.

`./bench selftest` makes no provider calls. It checks Python syntax, materializes the HauntedMC suite and compiles the separate Java benchmark source set:

```bash
./gradlew benchmarkCheck
```

The benchmark source set is not attached to normal `test`, `check`, `build`, or paid/live GitHub workflows.

## Commands

```bash
./bench list
./bench estimate smoke
./bench estimate standard

./bench run smoke
./bench run haunted
./bench run holdout
./bench run replays
./bench run longmemeval
./bench run longmemeval-oracle
./bench run memoryagentbench
./bench run standard
./bench run extended

./bench run haunted --category identifiers
./bench run haunted --case discord-explicit-aankondigingen
./bench run standard --sample 100
./bench run haunted --repeat 3

./bench compare <older-run> <newer-run>
./bench view <run>

./bench score-longmemeval <run>
./bench ragcheck <run>
./bench inspect <run>

./bench v2-setup --download-data
./bench v2-run --tier small --domain both --limit 20
```

AIlex configuration can be varied without editing `config.yml`:

```bash
./bench run haunted \
  --set openai.assistant.models.grounded.model=gpt-5.6-sol \
  --set openai.assistant.models.grounded.reasoning_effort=high
```

## Cost guards

`estimate` reports a deliberately conservative token envelope. Dollar limits are available only after creating a local pricing file because provider prices change independently from this repository:

```bash
mkdir -p benchmark/local
cp benchmark/pricing.example.json benchmark/local/pricing.json
# Fill in current provider prices.
./bench run standard --max-cost 25
```

If no local pricing file exists, `--max-cost` fails closed rather than relying on stale prices committed to source control.

Use `--limit`, `--sample`, `--category`, and `--case` to reduce a run before spending provider credits. LongMemEval-V2 has its own `--limit` because the official upstream harness owns execution and scoring.

## Suites

### `smoke`

A small real-model subset covering normal conversation, memory, live state, knowledge, canonical identifiers and abstention.

```bash
./bench run smoke
```

```bash
./bench run smoke --repeat 3
```

Use this after a prompt, retrieval, memory or model-configuration change. It tells you whether the most important AIlex product paths still work with a real provider call; hard failures identify broken canonical IDs, evidence, abstention or memory behavior, while the report exposes the route and retrieved context.

It is good for a fast development loop because it is small and product-specific. It is not a release-quality score: it has only 24 cases, model outputs vary, and its HauntedMC facts are visible in this repository. Run critical cases repeatedly and use holdouts/replays before treating an improvement as robust.

### `haunted`

Full-product scenarios. Most exact-identifier cases are generated from `src/main/resources/knowledge/entities.tsv`, so benchmark truth is not duplicated. Authored cases cover conversation continuity, durable memory formation/correction/forgetting, live state, abstention and multilingual identifier preservation.

```bash
./bench run haunted
```

```bash
./bench run haunted --category identifiers --repeat 3
```

```bash
./bench run haunted --case missing-server-evidence
```

This is the main product-regression suite. It tells you whether AIlex preserves exact server identifiers, attaches required evidence, respects current player memory, and abstains where server knowledge is missing. Category and case commands are useful while diagnosing one failure; use the full suite before accepting a product change.

It is good because its invariants are directly tied to HauntedMC’s user-facing contract. It is not independent external validation: generated cases derive from the same reviewed registry AIlex uses, and authored cases can be overfit. Pair it with private holdout/replay cases and published suites.

### `holdout`

```bash
./bench run holdout
```

Holdout loads the gitignored `benchmark/local/holdout.jsonl`. It tells you whether a change generalizes to product cases that prompt/code authors do not routinely inspect.

It is the best guard against tuning to `smoke` and `haunted`, provided it stays private and is refreshed occasionally. It is not useful if it is too small, stale, or exposed to the people optimizing prompts; never use private player data unredacted.

### `replays`

```bash
./bench run replays
```

Replays load sanitized failures from `benchmark/local/replays.jsonl`. They tell you whether known production incidents remain fixed under the real assistant pipeline.

They are excellent permanent regression tests because they are based on real failure modes. They are not a representative quality sample: replay-heavy changes can solve yesterday’s incidents while regressing unseen conversations. Keep private holdouts alongside them.

### LongMemEval

The runner downloads the official cleaned LongMemEval data. The default `longmemeval` suite uses the cleaned S variant; `longmemeval-oracle` uses evidence sessions only as a diagnostic. It still uses native routing, so it is not presented as an end-to-end upper bound.

The adapter inserts the timestamped benchmark history into AIlex as trusted benchmark event memory, then asks the original question through **native AIlex routing**, memory retrieval, reasoning and grounding. Relative timestamps are aligned to the local benchmark clock so phrases such as `yesterday` and `last week` retain their intended relationship to the benchmark question date. The adapter deliberately does **not** claim to measure AIlex's memory-formation extractor, because re-generating every historical assistant turn would change the published interaction and multiply model cost.

Fixture memory is ephemeral and cleared after every case. This prevents one published question's synthetic history from leaking into another and avoids measuring SQLite write throughput rather than retrieval. It does not bypass AIlex's memory search, ranking, temporal resolution or grounding. Each run records this boundary and its routing policy in `run.json` and every result row.

The console prints `hard=N/A` when a published case has no deterministic hard check. A LongMemEval result is not an official score until `./bench score-longmemeval <run>` has invoked the upstream scorer; do not treat console PASS/N/A labels as that score.

Large histories are written once to content-addressed JSONL fixtures under `benchmark/.cache/fixtures/` and referenced by the materialized cases instead of duplicating megabytes of history for every question.

`./bench score-longmemeval` writes the official `{question_id, hypothesis}` JSONL and invokes the upstream LongMemEval scorer rather than copying its scoring implementation into AIlex.

```bash
./bench run longmemeval --limit 20
```

```bash
./bench run longmemeval
```

```bash
./bench score-longmemeval <run-id>
```

```bash
./bench run longmemeval-oracle --limit 20
```

`longmemeval` tells you how well native AIlex routing, temporal memory retrieval and answer generation work on a published long-history benchmark. Score a completed run with the upstream scorer before comparing it to other LongMemEval results. `longmemeval-oracle` is a diagnostic with evidence-only sessions; it is useful for separating distractor-context difficulty from the rest of the task, but it is not an end-to-end upper bound.

This is good external evidence for long-context memory and relative-time handling. It is not a test of AIlex memory formation, Bukkit live state, or HauntedMC knowledge. The fixture is deliberately injected as trusted event memory, and the default report has `hard=N/A` for these rows. For example, the linked four-case report records `hard.evaluated: 0` and no semantic or official score: it confirms the run completed and provides traces, but it does **not** establish accuracy.

Upstream: <https://github.com/xiaowu0162/LongMemEval> (MIT).

### MemoryAgentBench

The runner loads `ai-hyz/MemoryAgentBench` through Hugging Face Datasets. The standard adapter uses the published exact-match and substring-exact-match subsets whose official metrics can be reproduced deterministically. Context is materialized once per upstream row and shared by all of its questions, preserving the benchmark's inject-once/query-many structure without creating multi-gigabyte duplicate fixtures. Questions use native AIlex routing; they are not forced onto a memory route.

Recommendation, duplicate LongMemEval and LLM-F1 subsets are not relabeled with home-grown scores; they can be added later only through their corresponding upstream metric protocol.

```bash
./bench run memoryagentbench --limit 20
```

```bash
./bench run memoryagentbench
```

This tells you how native AIlex routing and memory retrieval perform on the published exact-match and substring-exact-match subsets. Their deterministic metrics are calculated from the saved answers, so the report can reveal a genuine exact-answer regression without a model judge.

It is useful coverage beyond HauntedMC because its contexts and questions are externally authored. It is not a complete MemoryAgentBench leaderboard result: recommendation, duplicated LongMemEval and LLM-F1 subsets remain excluded until their official protocols are integrated. Its flattened text context also does not exercise AIlex live tools or multimodal input.

### `standard` and `extended`

```bash
./bench run standard --sample 100
```

```bash
./bench run standard
```

```bash
./bench run extended --sample 100
```

`standard` combines HauntedMC, cleaned LongMemEval-S and supported MemoryAgentBench cases; `extended` swaps in the larger LongMemEval-M set. They tell you whether a candidate change trades product behavior against published-memory behavior.

They are good pre-release comparison runs because they expose trade-offs across distinct capabilities. They are expensive and heterogeneous, so do not average their outcomes into one “intelligence” number. Compare hard invariants, upstream published scores, semantic judgments and latency separately, using the same materialized suite and configuration.

Upstream: <https://github.com/HUST-AI-HYZ/MemoryAgentBench> and <https://huggingface.co/datasets/ai-hyz/MemoryAgentBench> (MIT).

### LongMemEval-V2

LongMemEval-V2 runs through its **official upstream harness**, with `ailex_text` registered as a local Memory backend. Its Python adapter sends trajectory text to a persistent Java bridge backed by the real `AssistantMemoryService`, then returns AIlex memory-search results to the upstream reader. The upstream harness still owns question selection, prompt construction, reader calls, evaluator calls, latency collection and aggregated metrics.

Set it up separately because the upstream dependency stack is heavier:

```bash
./bench v2-setup --download-data
```

This creates `benchmark/.venv-v2`, checks out the upstream artifact, installs its Python package, installs the AIlex adapter into that local checkout, downloads the official data when requested, prepares it and validates the small tier.

Run a small evaluation with the upstream defaults:

```bash
./bench v2-run --tier small --domain both --limit 20
```

Or point the OpenAI-compatible reader at OpenAI while continuing to use the local AIlex memory backend:

```bash
./bench v2-run \
  --tier small \
  --domain both \
  --reader-model gpt-5.6-sol \
  --reader-base-url https://api.openai.com/v1 \
  --evaluator-model gpt-5.6-sol
```

The current adapter is deliberately **text-only**: screenshot fields and query images are ignored. Every V2 output directory therefore contains `AILEX_ADAPTER.json` with `text_only=true`, `screenshots_ignored=true` and `leaderboard_comparable=false`. The resulting score is useful for evaluating AIlex's memory on the published V2 tasks, but it must not be presented as a multimodal official-leaderboard score.

This is the strongest protocol check in the repository because the official harness owns the questions, reader, evaluator, latency collection and aggregated metrics. It is still not a multimodal leaderboard evaluation, and its reader/evaluator model choices can materially affect results; record those settings when comparing runs.

Upstream: <https://github.com/xiaowu0162/LongMemEval-V2> (Apache-2.0).

### RAGChecker

RAGChecker is used as a diagnostic evaluator, primarily for HauntedMC factual cases. The runner converts saved AIlex traces to the upstream `query`, `gt_answer`, `response`, `retrieved_context` format and invokes `ragchecker-cli`. This yields retriever and generator metrics without inventing a second RAG metric vocabulary.

Upstream: <https://github.com/amazon-science/RAGChecker> (Apache-2.0).

### Inspect AI

Inspect AI is an optional results/evaluation surface. AIlex runs first in Java; Inspect receives the saved question, target and **precomputed AIlex answer**. The custom solver therefore does not reimplement AIlex or replace its output with another model. Inspect can then apply its model-graded QA scorer and produce standard Inspect logs/viewer output.

Upstream: <https://inspect.aisi.org.uk/> (MIT).

## Result files

Normal AIlex runs contain:

- `suite.jsonl` — the exact materialized cases used for the run;
- `request.json` — non-secret runner settings and AIlex config overrides;
- `results.jsonl` — per-case and per-turn AIlex traces;
- `run.json` — commit/config/knowledge hashes and run metadata;
- `summary.json` — aggregate metrics;
- `report.html` — local browseable report;
- optional upstream evaluator outputs.

A result records route, mode/model, retrieved contexts, memory-context size, evidence IDs, hard-check outcome, model usage and latency. This is intended to answer *why* a case failed, not merely whether it failed.

V2 runs retain the upstream directory structure and `aggregated_metrics.json` files. A two-domain run also creates `combined_metrics.json` with the upstream combination script plus the AIlex adapter disclosure described above.

## Scoring and failure diagnosis

Hard checks cover properties that should not be traded away: exact identifiers, required evidence, forbidden text, explicit abstention behavior and deterministic exact-match metrics.

Semantic judging is enabled automatically for HauntedMC, holdout and replay reference cases unless `--judge none` is supplied. `--judge all` can grade every case with a reference, but published suites should still use their upstream scorer for externally meaningful numbers.

Reports classify obvious failures into retrieval, grounding, generation, delivery/liveness, selection/context, and product-invariant buckets. The classification is diagnostic, not a replacement for inspecting the trace.

## Comparing runs

`./bench compare` pairs rows by case ID and repetition. It reports hard regressions/improvements, semantic score movement and latency deltas. It does not average unrelated dimensions into one number.

For stochastic comparisons, use the same materialized suite and configuration except for the dimension being tested, and run critical HauntedMC cases for multiple repetitions.

## Private holdout and production replays

Keep a local holdout that prompt/code authors do not routinely inspect:

```text
benchmark/local/holdout.jsonl
```

Store sanitized real failures separately:

```text
benchmark/local/replays.jsonl
```

Never put raw UUIDs, private conversations, IP addresses, credentials, moderation notes or other sensitive player/server data into benchmark fixtures.
