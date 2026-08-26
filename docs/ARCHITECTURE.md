# AIlex Architecture

AIlex is a bounded cognitive assistant runtime around Paper and Citizens. Deterministic server code owns permissions, privacy, capability ceilings, memory acceptance and answer validation. The language model performs interpretation, source selection and generation inside that boundary; it never receives arbitrary server mutation or plugin access.

## Design principles

AIlex optimizes for five properties simultaneously:

1. **Grounded correctness** — server-specific and live claims require attributable positive evidence.
2. **Selective lifelong memory** — durable identity is represented as typed claims/events/relationships, not transcript replay.
3. **Temporal consistency** — corrections and historical states remain distinguishable instead of being flattened into one value.
4. **Bounded agency** — information seeking is allowed only through registered read tools and strict call/deadline budgets.
5. **Low intervention cost** — deterministic routing/retrieval runs before model planning, and proactive behavior stays out of likely player conversations.

## Request flow

A direct addressed message or accepted follow-up enters `AssistantChatController`. The controller records compact dialogue state, freezes permitted Paper state on the server thread and submits the request to `AssistantRequestCoordinator`. Direct and follow-up requests have priority over proactive work, and queue capacity is bounded.

`AssistantService` then performs:

1. deterministic intent/language analysis and capability planning;
2. safe immutable live-state capture;
3. query-ranked typed-memory retrieval;
4. reviewed knowledge retrieval or discovery;
5. an information-gain decision on whether a bounded planner/read-tool call can add missing evidence;
6. route-budgeted context compilation;
7. adaptive model generation;
8. line-level evidence validation;
9. validated semantic-memory and verified-experience updates;
10. diagnostics, caching and delivery.

The read-agent is not the default cost path. Complete live state or strong reviewed/memory evidence skips planning.

## Dialogue and working memory

`AssistantConversationManager` retains a compact active player↔assistant multi-turn window for natural follow-ups. Ambient chat is a separate short-lived source included only when `WorkingContextPolicy` judges it useful.

Working context and durable memory intentionally solve different problems:

- working context preserves immediate conversational state;
- claim memory preserves selected current/historical beliefs;
- event/episode memory preserves meaningful occurrences;
- relationship edges preserve factual entity relationships;
- procedural experience stores verified lessons about assistant strategies;
- reviewed knowledge stores operator-controlled HauntedMC facts.

Raw conversation replay is not AIlex's long-term identity.

## Context planning and token economics

`RequiredContextPlanner` is a fast deterministic prior and permission-safe source planner. `ContextCompiler` allocates each route's input ceiling across the current request, active dialogue, live state, durable memory, reviewed evidence and optional short-lived chat. The bounded model planner can refine missing information needs only within already-permitted capabilities.

Configured token limits are ceilings, not targets. Results are ranked, deduplicated and clipped before prompt assembly. The read loop is information-gain gated and defaults to one round, leaving room for final generation and bounded corrective escalation. Planner input/output tokens are counted separately.

## Live Minecraft state

Live data is captured synchronously on the Paper thread and copied into an immutable snapshot before asynchronous model work. For grounded live requests with the read-agent enabled, `AssistantLiveCapturePolicy` freezes the authorized safe superset required by any permitted later `inspect_live` call; the direct prompt still includes only planner-selected sources.

Safe sources include requester state, bounded inventory/equipment, world/target context, nearby entity summaries, player-safe server health, NPC state and trusted custom feature facts from `AssistantContextProvider`s. Integration data passes deterministic safety checks. Credentials, network identifiers, staff-only information, reports/sanctions, configuration, filesystem paths and infrastructure secrets are excluded before model invocation.

## Reviewed knowledge and neural hybrid retrieval

`LocalKnowledgeIndex` combines complementary retrieval signals:

- BM25-style lexical relevance;
- exact command/title/alias matching and phrase boosts;
- multilingual concept expansion;
- learned OpenAI semantic embeddings;
- reciprocal-rank fusion between lexical and semantic rankings;
- authority/expiry filtering;
- redundancy and diversity suppression.

Exact commands and HauntedMC terminology remain lexical-first. Learned embeddings recover paraphrases and meaning-level matches. Corpus vectors warm asynchronously; if the embedding endpoint is cold/unavailable, player requests continue with lexical retrieval instead of synchronously embedding the entire corpus. Query/document vectors and search results are bounded/cached.

## Typed bounded read-agent

`AssistantTool` and `AssistantToolRegistry` form the model-facing capability boundary. The current registry contains only explicit read capabilities:

- `search_memory`;
- `search_memory_timeline`;
- `search_experience`;
- `search_knowledge`;
- `inspect_live` over the frozen safe snapshot.

Each tool owns its strict schema, availability predicate and deterministic executor. The planner cannot discover arbitrary Java/plugin methods, execute commands, access SQL/filesystem internals or create capabilities. Unknown/malformed/disallowed tool requests fail closed.

The planner may iterate only up to configured model/tool/deadline budgets. Temporal recall can request a timeline; a server-specific evidence miss can reformulate a focused knowledge query. Ordinary vanilla gameplay stays out of the planner loop and can use general Minecraft knowledge directly.

## Memory fabric

`AssistantMemoryService` maintains an audience-indexed hot store backed by `MemoryRepository`. `MemoryRecord` remains the compact backward-compatible durable storage envelope, while the cognitive layer exposes separate semantic views:

- `MemoryClaim` — what AIlex believes, with subject/predicate/object, source authority, confidence, salience, validity, status and evidence;
- `MemoryEvent` — what happened at a point in time;
- `MemoryEpisode` — an ordered aggregate of related events;
- `MemoryEdge` — an evidence-backed relationship between entities;
- procedural experience — verified lessons about how AIlex should retrieve/respond.

This separation prevents “what happened”, “what is believed”, “how entities relate” and “what strategy worked” from collapsing into one semantic record type.

### Formation, correction and forgetting

Model-proposed memory operations are accepted only when deterministically supported by the source message, non-sensitive and correctly scoped. Shared learned memory is fact-only and permission-gated.

Stable semantic keys make updates deterministic. A changed value receives a new validity start; the previous version retains its historical interval and becomes superseded. Cross-kind conflicts for one player key are removed from the active view. Explicit forgetting tombstones the selected meaning. Repeated confirmation reinforces an existing value rather than generating duplicate history.

## Temporal truth and epistemology

`MemoryTruthResolver` resolves `MemoryClaim`s at an arbitrary point in time. It filters by validity and scores source authority, confidence, recency, salience and scope. Explicit supersession is deterministic. Near-tied conflicting values are exposed as `DISPUTED` rather than silently chosen.

Player authority is proposition-dependent: explicit player-owned preferences are strong evidence about that player; an ordinary player does not become authoritative for global server facts merely by stating them. Trusted/reviewed/runtime sources carry higher global authority. The LLM never decides which repository row is freshest.

## Shared/network identity

SQLite/WAL is the self-contained development/single-runtime backend. MySQL is the authoritative multi-runtime backend for one network-wide AIlex memory identity.

Player-facing reads use the in-memory audience index only. Shared synchronization runs off-thread and consumes a database-owned monotonic change sequence in bounded pages, including tombstones. This avoids Paper tick-thread network I/O, wall-clock skew and dropped bursts larger than one page.

If MySQL is explicitly configured but unavailable at startup, AIlex uses a non-persistent fail-safe instead of silently creating divergent per-server SQLite histories. A multi-runtime deployment should treat `shared_memory=false` as an operational fault to fix, not as a second authoritative identity.

## Verified procedural experience

`AssistantExperienceMemoryService` stores compact lessons only from deterministic acceptance/rejection/tool outcomes or externally grounded corrections. Experience is NPC-scoped and strategy-only when injected into reasoning; it cannot masquerade as factual evidence about a player or server. Free-form model self-criticism is never promoted directly to durable experience.

Full episodic consolidation and learned long-horizon strategy statistics remain later-roadmap work; 1.7 establishes the safe representation and verified write path they require.

## Evidence packets and claim-level grounding

`EvidencePacket` normalizes all model-citable provenance IDs. Grounded replies additionally contain `claim_evidence`, mapping each zero-based output line to exact supporting IDs.

Unknown IDs are rejected. Positive server/live/memory answers require positive evidence from the appropriate provenance family. Negative lookup observations such as `knowledge.none`, `memory.none` and `live.*.none` are useful planner context but **cannot validate a factual player-facing answer**; negative-only retrieval therefore falls through to AIlex's safe abstention/fallback path. Evidence-bearing multi-line output is invalid if any line lacks an evidence mapping.

This is fail-closed by design: a retrieval miss never converts into permission to hallucinate.

## Social conversation model

`SocialConversationGraph` is the single transient model used to decide whether AIlex should intervene in public chat. It combines:

- a bounded volatile recent speaker/message window for direct-address history, alternation and contextual follow-ups;
- decaying pair edges for short-lived interaction strength.

Neither layer is persisted. The graph is not a friendship score, affection model, psychological profile or durable transcript. `ProactiveInterventionPolicy` combines this graph with public-question detection; likely player-to-player continuations are suppressed while explicit broadcast questions remain eligible. Silence is a valid successful action.

Full persistent social-world modelling and utility-learned intervention policy remain later-roadmap work.

## Evaluation and observability

`AIlexBench` is the deterministic cognition regression harness. Focused suites extend it for semantic-only retrieval, memory temporal validity, shared synchronization, typed tool execution, evidence provenance, privacy and social intervention. CI does not depend on a live OpenAI call, so stochastic provider output cannot hide control-plane regressions.

JaCoCo coverage is enforced in CI at a regression floor (55% line / 40% branch). Provider usage accounting tracks input/output/cache tokens; the read-agent tracks planner calls/tool calls and planner input/output tokens. Completion diagnostics include route/model/outcome, retrieved chunk count, evidence IDs, claim-evidence count and latency.

Historical 1.6 model-quality scores are not retroactively invented. The stable benchmark/evidence instrumentation established here is the baseline framework for longitudinal release comparisons and replayed production-safe scenarios.

## Safety boundary

The model can generate player-facing text, request registered bounded read tools and propose memory candidates. It cannot:

- execute commands or mutate the Minecraft world;
- change economy/moderation state;
- write arbitrary files/database rows;
- inspect arbitrary plugins/configuration;
- access credentials/infrastructure internals;
- control NPC movement/actions directly;
- bypass memory validation, capability permissions or data redaction.

Write-capable/embodied actions belong behind separately permissioned plan→validate→execute capabilities in a later release, not in the 1.7 read-cognition path.

## Roadmap boundary

1.7 deliberately establishes the memory/evaluation foundation plus the typed read-agent and transient social capabilities already present in this PR. The deeper roadmap remains staged: semantic routing refinements, consolidation/entity-graph learning and richer lifelong policy learning come later; write-capable embodied actions remain a 2.0-class concern. This preserves the evaluation requirement to improve the architecture without collapsing every research direction into one untestable release.
