# AIlex chat evaluation

Player-facing quality is an evaluated product property, not something inferred from architectural complexity.
The deterministic test suite remains the CI safety net, while this document defines the live-model evaluation that should be
run when changing prompts, models, retrieval strategy, context construction or memory behavior.

## Why separate evaluation layers

A unit test can prove that a request was routed, a source ID was checked or a correction superseded an older value. It cannot
prove that a real model response is natural, complete, helpful or robust across a sustained conversation. Conversely, a model
judge can prefer a fluent answer while missing a privacy or provenance violation. AIlex therefore keeps deterministic invariants
and model-facing quality evaluation separate.

The benchmark design is informed by:

- [LongMemEval (Wu et al., ICLR 2025)](https://proceedings.iclr.cc/paper_files/paper/2025/hash/d813d324dbf0598bbdc9c8e79740ed01-Abstract-Conference.html), especially its separation of information extraction, multi-session reasoning, temporal reasoning, updates and abstention.
- [RAGChecker (Ru et al., NeurIPS 2024)](https://proceedings.neurips.cc/paper_files/paper/2024/hash/27245589131d17368cccdfa990cbf16e-Abstract-Datasets_and_Benchmarks_Track.html), especially its fine-grained separation of retrieval and generation failures instead of relying on one end-to-end score.
- [Lost in the Middle (Liu et al., TACL 2024)](https://aclanthology.org/2024.tacl-1.9/), which motivates explicit context-position and distractor tests rather than assuming a larger context window is automatically better.

These references shape the evaluation protocol; AIlex does not claim benchmark-equivalent scores.

## Reference suite

Maintain a stable suite of roughly 300–500 conversational cases. A useful minimum distribution is:

| Category | Approx. cases | What it tests |
| --- | ---: | --- |
| Natural conversation and follow-ups | 70 | continuity, pronouns, corrections, tone, useful answer length |
| HauntedMC reviewed knowledge | 70 | retrieval recall, claim correctness, exact identifiers, negative/existence questions |
| Long-term player memory | 55 | correct recall, scope, relevance, selective use, privacy |
| Updates and temporal questions | 40 | current vs historical truth, correction handling, stale-data avoidance |
| Abstention and missing evidence | 35 | no hallucinated channels/commands/facts, useful uncertainty responses |
| Live Minecraft state | 35 | requester/world/NPC/server evidence and stale-snapshot resistance |
| Multi-source questions | 30 | combining memory + knowledge + live state without provenance mixing |
| Multilingual and social style | 30 | Dutch/English/German consistency, exact-name preservation, non-helpdesk tone |

Cases should include short conversations as well as sustained sessions long enough to overflow the recent-turn window and exercise
mid-term state. Every factual HauntedMC case should include an expected evidence set or an explicit `no evidence` condition.

## Metrics

Do not collapse everything into one score. Track at least:

1. **Answer correctness** — whether the response answers the actual question correctly.
2. **Claim faithfulness** — proportion of factual claims supported by the evidence the turn was allowed to use.
3. **Retrieval recall** — whether the evidence required for the gold answer reached the generator.
4. **Context precision** — how much supplied context was actually relevant; useful for detecting token bloat.
5. **Exact-identifier accuracy** — commands, Discord channels, ranks, currencies, warps, URLs and server names must be copied exactly.
6. **Abstention quality** — unsupported questions should not be converted into confident inventions.
7. **Conversation continuity** — references such as “that”, “why?”, corrections and older-session topics resolve to the intended context.
8. **Memory update accuracy** — corrected current truth is used while historical truth remains queryable.
9. **Naturalness/completeness** — useful server-member style without artificial one-sentence compression or unnecessary essaying.
10. **Operational cost** — input/output tokens, cached tokens, model calls and tool calls.
11. **Latency** — p50, p95 and timeout/fallback rate.
12. **Liveness** — every explicit valid AIlex mention reaches a traceable terminal outcome; silent drops are a zero-tolerance failure.

## Quality gates

For a production candidate, run the same case set against a fixed knowledge snapshot and an agreed baseline.
Recommended hard gates are:

- zero silent explicit-mention failures;
- zero unsupported exact HauntedMC identifiers in the reviewed identifier subset;
- all privacy/scope deterministic tests pass;
- no statistically meaningful drop in factual correctness or claim faithfulness;
- retrieval recall must not be traded away merely to reduce prompt size;
- p95 latency and average model calls must remain within the configured operational envelope.

Naturalness should use blind pairwise comparison against a fixed baseline plus human review of a stratified sample. An LLM judge
can accelerate review, but it is not ground truth: judge order should be randomized and at least a subset should be manually checked.

## Diagnostics for a failed case

Classify a failure before changing prompts:

1. **Routing failure** — wrong intent/context family selected.
2. **Retrieval failure** — required evidence never reached the generator.
3. **Selection/context failure** — evidence was retrieved but discarded, clipped or buried.
4. **Generation failure** — evidence was present but the model reasoned or phrased incorrectly.
5. **Grounding failure** — unsupported output passed validation.
6. **Memory failure** — wrong scope, stale truth, missed correction or irrelevant personalization.
7. **Delivery/liveness failure** — good generation never reached the player.

This breakdown prevents the common mistake of changing the model or increasing context size when the real defect is elsewhere in the pipeline.
