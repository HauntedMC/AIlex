# Testing and Quality

Run the complete build before merging:

```bash
./gradlew clean build
```

The build compiles production/test code, runs JUnit, Checkstyle and JaCoCo. CI additionally runs `jacocoTestCoverageVerification`; the current regression floor is 55% line coverage and 40% branch coverage. Cognitive behavior is split into deterministic control-plane tests and provider/model integration behavior so stochastic upstream output cannot hide regressions in routing, memory, evidence handling or intervention policy.

## AIlexBench

`src/test/java/nl/hauntedmc/ailex/assistant/bench/AIlexBenchTest.java` is the deterministic intelligence regression harness. It does not call OpenAI. Every benchmark case must pass in CI.

The current benchmark and focused regression suites cover:

- intent and mode routing for server facts, live state, memory/event recall and conversation;
- selective durable-memory extraction signals;
- real semantic-vs-lexical retrieval fusion behavior through a deterministic embedding fake;
- temporal correction resolution and historical validity;
- explicit claim/event/episode/relationship-edge memory semantics;
- full line-level evidence coverage and evidence-source classes;
- typed read-tool capability availability, execution and negative evidence;
- privacy rejection for identifiers/coordinates and cross-player scope isolation;
- proactive abstention inside player dialogue and broadcast override;
- shared-memory pagination/convergence and hot-cache-only player reads;
- planner call, token and final-answer deadline budgets.

When a production incident exposes a new deterministic cognitive failure mode, add a benchmark case or a focused regression test before changing the implementation. Provider/model quality should be measured separately with replayed production-safe scenarios; deterministic CI must never depend on a live OpenAI endpoint.

## Routing, context and call economy

Tests should verify:

- direct/follow-up intent classification;
- live-state questions select the minimum direct source family needed;
- the frozen live capability ceiling contains every permitted source an async read tool may inspect;
- the current request is never dropped when context is clipped;
- multi-turn working context survives compilation;
- excessive history/evidence remains under route ceilings;
- ordinary FAST chat stays on the plain-text path;
- explicit first-person durable information activates the structured envelope without a second extraction call;
- grounded/deliberate escalation only occurs when model-call and deadline budget remains.

Manual prompts include `waar ben ik?`, `welk bioom is dit?`, `wat houd ik vast?`, `wat is mijn rank?`, a custom server fact, and a short contextual follow-up.

## Neural hybrid retrieval

Retrieval tests cover two distinct layers.

### Lexical precision

Verify exact commands, aliases, Dutch/English concept expansion, phrase boosts, expiry handling, redundancy suppression and broad discovery. Exact `/command` queries should not lose precision because the semantic layer exists.

### Learned semantic recall

`NeuralHybridRetrievalTest` uses a deterministic fake `SemanticEmbeddingProvider` so CI proves fusion without a network dependency. At least one case must have no useful lexical overlap and succeed only because the learned vector is semantically aligned. A second case verifies that an unavailable embedding provider falls back to lexical retrieval without invoking embeddings.

The production `OpenAiEmbeddingProvider` uses learned embeddings. Corpus vectors warm asynchronously; a cold or unavailable embedding path leaves BM25/exact retrieval usable instead of blocking a player request while the complete corpus is embedded.

Production smoke testing should additionally confirm the configured embeddings endpoint is available and `/ailex ai status` reports learned semantic retrieval as active. Per-request assistant diagnostics record retrieved chunk counts and evidence IDs; status exposes semantic availability and read-agent call/token totals.

## Memory fabric and temporal truth

`MemoryRecord` remains the durable repository envelope for backward-compatible SQLite/MySQL storage. The cognitive layer separates:

- `MemoryClaim`: evidence-backed belief with authority, temporal validity and status;
- `MemoryEvent`: typed observation of something that happened;
- `MemoryEpisode`: ordered aggregate of related events;
- `MemoryEdge`: evidence-backed relational graph edge;
- verified procedural experience: NPC-scoped lessons derived from externally grounded outcomes.

Memory tests must cover:

- SQLite/WAL persistence;
- repository hot-index reload;
- sensitive/invented candidate rejection;
- player vs trusted shared scope;
- shared learned memory accepting facts only;
- stable-key correction/supersession;
- historical timeline visibility;
- current-vs-historical truth resolution;
- disputed near-tied claims;
- cross-kind semantic replacement;
- explicit forgetting;
- preferences, facts, opinions, interests and temporary goals;
- relationship/event scoping;
- repeated-confirmation reinforcement;
- associative one-hop recall and duplicate suppression;
- normal-query relevance gating so unrelated high-salience memory is not replayed;
- broad profile recall remaining available when the player explicitly asks what AIlex remembers.

Assertions should distinguish the active view from historical rows. A corrected value receives a new validity start; it must not retroactively become true before the correction occurred.

## Shared memory repository

SQLite remains the self-contained development/single-runtime backend. MySQL is the shared authoritative backend for one AIlex identity across simultaneously running servers. Player-facing reads use the in-memory audience index only; shared database synchronization runs off-thread.

Shared synchronization uses a database-owned monotonic change sequence rather than server wall-clock timestamps. Tests cover pagination beyond one 2,048-row batch so bursts cannot silently lose updates.

Operational checks for shared memory:

1. runtime A writes a safe player/shared fact;
2. runtime B sees it after `shared_sync_seconds`;
3. a correction replaces the active value on both runtimes;
4. forgetting/tombstoning removes the active value on both runtimes;
5. database unavailability never performs synchronous network I/O on the Paper tick thread;
6. when MySQL is explicitly selected but unavailable at startup, AIlex uses a non-persistent memory fail-safe rather than silently creating divergent per-server SQLite identities.

## Verified procedural experience

`AssistantExperienceMemoryServiceTest` verifies that procedural lessons are NPC-scoped `EPISODE` records tagged `experience`, `procedural` and `verified`, and that recall filters out ordinary NPC episodes or player memory.

Experience must only be written from deterministic acceptance/rejection/tool outcomes or other trusted signals. Free-form model self-criticism is never durable experience by itself. Experience observations are strategy-only context and are not exposed as factual evidence IDs for player-facing claims.

## Evidence packets and claim-level grounding

`EvidencePacket` is the normalized deterministic envelope for reviewed knowledge, memory and live evidence. `AssistantReply.claimEvidence` maps each emitted factual line to exact evidence IDs.

Grounding tests must verify:

- unknown source ID rejection;
- correct source-family requirement for server/live/memory routes;
- deterministic negative evidence (`knowledge.none`, `memory.none`, `live.*.none`) can support an abstention but not an invented fact;
- `claim_evidence` indexes within the emitted line range;
- every evidence-bearing output line has at least one mapping;
- the union of cited IDs is covered by the line mappings;
- partial multi-line grounding becomes invalid;
- plain casual chat remains valid without artificial citations;
- invalid grounded output can escalate only within the configured call/deadline budget;
- final fallback abstains instead of fabricating a HauntedMC fact.

`AssistantReplyTest`, `AssistantGroundingPolicyTest` and `EvidencePacketTest` form the fail-closed evidence contract.

## Bounded typed read-agent

`AssistantTool` and `AssistantToolRegistry` are the model-facing capability boundary. The planner never receives arbitrary Java/plugin access: each tool owns a strict schema, deterministic permission predicate and bounded executor. The current registry exposes memory search/timeline, verified experience recall, reviewed knowledge search and frozen live-state inspection only when their configured capabilities are allowed.

The read-agent should be tested as an evidence-acquisition controller, not as an open-ended autonomous agent. Important cases:

- a complete live snapshot skips planning;
- strong reviewed server evidence skips planning;
- normal vanilla gameplay help remains cheap when deterministic evidence is sufficient;
- a temporal memory question may request `search_memory_timeline`;
- a server-specific evidence miss may perform a focused knowledge search;
- only registered and permitted read tools can execute;
- malformed/unknown tool calls fail closed;
- per-round call count and total model-call/deadline budgets are enforced;
- planner input/output token usage is observable;
- tool output contains only already-authorized memory/knowledge/frozen-live facts.

No test should grant command execution, arbitrary SQL, filesystem access or plugin reflection to prove agent behavior.

## Proactive community intelligence

False positives matter more than response rate. `SocialConversationGraph` is now the single transient social/thread model. It combines decaying pair edges with a bounded volatile speaker/message window used for direct-address history and alternation detection. The window is never persisted and is not a friendship, affinity or psychological profile.

Test at least:

1. a self-contained public question with no active conversation;
2. a question directly naming/tagging another player;
3. two players alternating messages followed by a contextual `?` reply;
4. recent direct-address history followed by a question without the name;
5. a strong transient social-pair connection suppressing contextual intervention;
6. pair strength and thread history decaying/pruning after their windows;
7. an explicit broadcast question (`weet iemand...?` / `anyone know...?`) overriding suppression;
8. shared cooldown behavior;
9. direct-request capacity remaining available while proactive work exists.

## Live integration and privacy

Provider tests should ensure:

- invalid/sensitive keys are rejected;
- IP addresses, e-mail/UUID/credential-shaped values are rejected;
- oversized facts are bounded;
- provider failure does not fail the whole assistant request;
- provider-qualified keys prevent silent collisions;
- other-player private state is never exposed through requester context.

Never test privacy by intentionally sending a secret to the model and hoping the prompt removes it later. The data boundary must reject unsafe context before model invocation.

## Reliability and concurrency

Cover:

- invalid structured-output retry limits;
- circuit-breaker behavior;
- direct/follow-up queue priority;
- same-player queued-turn replacement;
- bounded load under many requests;
- context-fingerprinted static caching;
- embedding failure fallback;
- shared-memory backend fallback;
- deadline exhaustion and safe response fallback;
- request traces accurately reporting route/model/tool outcome.

## Manual production smoke test

After deploying to a test server, inspect:

```text
/ailex ai status
/ailex ai usage
/ailex memory status
/ailex trace recent
```

Then exercise: a normal conversation, multi-turn follow-up, live biome/item/custom-feature queries, exact-command server knowledge, a semantic paraphrase, open-ended discovery, explicit first-person fact/preference/goal, correction, explicit forget, temporal recall, a two-player conversation AIlex must not interrupt, and an explicit broadcast question it may answer.

For a multi-runtime deployment, repeat a memory correction/forget across two servers and confirm active state converges through the shared repository.
