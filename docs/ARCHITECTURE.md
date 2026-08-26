# AIlex 1.5 Architecture

AIlex 1.5 separates Minecraft chat ingress, conversational state, request admission, context selection, durable memory, retrieval, inference, and delivery. The assistant path is deliberately read-only: it may inspect trusted Paper/Bukkit state but it never executes commands, edits the world, performs economy/moderation actions, or mutates NPC behaviour from an LLM response.

## Runtime Flow

```text
Paper AsyncChatEvent
  -> AssistantChatListener (thin Paper adapter)
  -> AssistantChatController
       -> AssistantMentionMatcher / WorkingContextPolicy
       -> AssistantConversationManager
       -> AssistantRequestCoordinator + AssistantRequestTracer
  -> AssistantService.prepare()
       -> AssistantIntentClassifier
       -> RequiredContextPlanner
       -> selective Paper/Bukkit snapshot
       -> typed Memory V2 lookup
  -> async AssistantService.respond()
       -> LocalKnowledgeIndex when required
       -> ContextCompiler + route token budget
       -> Luna / Terra / Sol model profile
       -> validation / bounded escalation
  -> Bukkit main-thread delivery
```

Meaningful server events are recorded separately by `AssistantEventMemoryService`; they do not travel through raw chat history.

## Reliability and Admission

`AssistantRequestCoordinator` replaces the old boolean request gate.

- Direct player requests have highest priority.
- Active-session follow-ups are admitted ahead of proactive work.
- One active request is allowed per player.
- A bounded global queue holds direct/follow-up work when capacity is occupied.
- A newer queued turn for the same player supersedes the older queued turn instead of creating an unbounded backlog.
- Proactive work is rejected first under pressure and never consumes direct-request queue capacity.
- Every direct request receives a lifecycle trace and every rejected/queued/upstream-failed path has explicit player feedback.

`AssistantRequestTracer` records terminal state and latency without storing prompt text. Operators can inspect it with `/ailex trace recent [player] [limit]`.

## Conversation State

`AssistantConversationManager` owns compact player↔NPC working state independently from durable memory and raw server chat.

It tracks:

- recent user/assistant turns;
- unresolved/pending answer state;
- previous intent;
- last user and assistant messages;
- active NPC target and inactivity timeout.

This lets short messages such as `haunty?`, `waarom?`, or `nee ik bedoel die vorige ronde` continue the active conversation without repeating the NPC name or losing the unresolved topic.

## Routing and Context Planning

`AssistantIntentClassifier` performs deterministic first-pass routing, including conversation, context follow-up, memory recall, event recall, server fact, live state, gameplay help, support, and safety.

`RequiredContextPlanner` then chooses the minimum trusted sources required for that intent. Examples:

- casual conversation: no knowledge retrieval and no live world dump;
- vanilla gameplay: general model knowledge plus local knowledge only when useful;
- server fact: local reviewed knowledge, no unrelated live snapshot;
- held-item/player-state question: requester state only;
- nearby question: nearby source without automatically adding global server state;
- event recall: typed episodic memory rather than raw transcript search.

`ContextCompiler` assembles these sources under hard route-specific token ceilings. Current defaults are 1,000 fast, 2,800 grounded, and 4,800 deliberate input tokens. The current turn is always preserved; active dialogue and live state outrank durable memory and retrieved evidence.

## Inference Profiles

Adaptive mode uses three independent profiles:

- **Fast — GPT-5.6 Luna / low reasoning** for ordinary conversation and cheap turns.
- **Grounded — GPT-5.6 Terra / medium reasoning** for factual and evidence-oriented work.
- **Deliberate — GPT-5.6 Sol / high reasoning** for the small set of complex requests that justify it.

Ordinary fast chat uses plain text rather than paying the structured-output overhead. Grounded work uses the reply contract when verification matters. A grounded request may perform one bounded escalation to the deliberate profile when the first result cannot be accepted and deadline/model-call budget remains.

## Memory V2

Durable assistant memory lives in `plugins/AIlex/assistant-memory.db`.

The repository stores typed `MemoryRecord` objects with:

- scope (`GLOBAL`, `PLAYER`, `NPC`, `PLAYER_NPC`, `WORLD`, `SESSION`, `EVENT`);
- kind (preference, fact, relationship, episode, event);
- subject/relation identifiers;
- key/value;
- confidence and salience;
- source type/source id;
- observation/occurrence timestamps;
- expiry and supersession metadata;
- tags.

Reads come from a hot in-memory active-record map. SQLite persistence uses WAL and a dedicated single writer, so disk writes are not on the chat hot path. Sensitive/invented memory candidates are rejected before persistence.

Player↔NPC relationship state is factual only (for example interaction count). AIlex does not infer psychological traits, affection, mood, or hidden player attributes.

### Event Memory

`AssistantEventMemoryService` intentionally records selected events instead of every Bukkit event. Built-in examples include session transitions, world/gamemode changes, deaths and advancements. Integrations can call the custom event API for meaningful HauntedMC events such as event wins or feature-specific milestones.

Different event categories have bounded TTLs and salience so transient events naturally expire while important achievements can remain recallable longer.

## Knowledge Retrieval V2

`LocalKnowledgeIndex` remains local and inspectable. It does not require an external vector database.

Ranking combines:

- BM25 lexical relevance;
- title and command-alias boosts;
- phrase matching;
- multilingual concept expansion;
- a local hashed dense-similarity signal;
- expiry filtering;
- near-duplicate suppression;
- per-request chunk/evidence budgets.

Reviewed Markdown/text files in `plugins/AIlex/knowledge/` remain the authoritative source for custom HauntedMC facts. General Minecraft knowledge is not artificially disabled when local evidence is unnecessary.

## OpenAI Boundary

`OpenAiResponsesClient` uses the Responses API with `store=false` by default and stable prompt-cache routing keys. Stable safety/persona/policy instructions are kept ahead of dynamic request/context content to maximize prefix reuse.

The client parses provider-reported usage for every HTTP response and maintains cumulative counters for:

- calls and successful calls;
- input tokens;
- cached input tokens;
- cache-write tokens;
- output/total tokens;
- cache-hit ratio.

This accounting also covers callers using the backward-compatible String-returning methods. Inspect it with `/ailex ai usage` or `/ailex ai status`.

## Persistence Boundaries

AIlex 1.5 distinguishes three kinds of state:

1. **Working dialogue** — compact in-memory active conversation state.
2. **Raw chat fallback** — bounded `ChatContextStore`; disk persistence is disabled by default and is opt-in.
3. **Durable memory** — typed SQLite Memory V2.

The old assistant YAML memory files are migration inputs only and are no longer bundled or recreated.

## Operational Diagnostics

Useful production commands:

- `/ailex ai status` — accepted/fallback counts, active traces and cumulative provider usage.
- `/ailex ai usage` — provider token/cache counters.
- `/ailex trace recent [player] [limit]` — recent request lifecycle results.
- `/ailex memory status` — active typed-memory counts.
- `/ailex memory recent` — recent memory metadata without dumping private values.
- `/ailex ai rebuild-index` — reload knowledge and memory indexes.

The test suite contains direct regressions for the original ignored-Haunty incident, active follow-up routing, queue supersession, bounded admission pressure, Memory V2 migration/supersession, selective context planning, hybrid retrieval and provider usage accounting.
