# Configuration

`src/main/resources/config.yml` is the source of truth for shipped settings. Production overrides should stay small; change a default only when measurements or deployment topology justify it. On startup and reload, AIlex aligns the active configuration with this current schema: existing values for valid keys are preserved, missing keys receive the bundled default, and unknown keys are removed.

## API and adaptive inference

`openai.api_key` configures the provider credential. `store_responses` is disabled by default. `request_timeout_seconds` bounds upstream calls.

The assistant uses three profiles under `openai.assistant.models`:

- `fast` — ordinary conversation and low-cost turns;
- `grounded` — factual, live-state and evidence-backed work;
- `deliberate` — difficult reasoning or quality escalation.

The main hard bounds are:

```yaml
assistant:
  total_deadline_seconds: 18
  max_model_calls: 4
  max_tool_rounds: 2
```

Two bounded tool rounds allow an ambiguous grounded request to gather a second discriminating source when the first result is insufficient. They are ceilings, not a default call sequence: `AssistantReadAgent` skips planning when deterministic retrieval already supplied the required source, and generation stops as soon as a verified answer is available. The four-call ceiling still prevents an information-seeking turn from expanding into an open-ended agent loop.

## Context budgets

```yaml
context:
  max_input_tokens_fast: 4000
  max_input_tokens_grounded: 12000
  max_input_tokens_deliberate: 24000
```

These are ceilings, not targets. `ContextCompiler` prioritizes the current request and useful evidence, then allocates space to dialogue, live state, memory, reviewed knowledge and optional recent chat. Larger ceilings are intended to stop useful memory/evidence from being prematurely clipped; they do not compensate for weak retrieval and are not automatically filled on every turn.

## Neural hybrid knowledge retrieval

```yaml
retrieval:
  hybrid_enabled: true
  max_chunks: 12
  max_evidence_characters: 32000
  query_cache_seconds: 300
  exclude_expired: true
  semantic_embeddings:
    enabled: true
    model: "text-embedding-3-small"
    dimensions: 512
    timeout_seconds: 8
```

The index fuses BM25/exact/phrase/concept signals with learned semantic similarity and reciprocal-rank fusion. Exact commands and server vocabulary remain lexical-first; embeddings mainly improve paraphrase/meaning recall. Corpus vectors warm asynchronously and query/document vectors are cached. A cold, failed or unavailable embeddings path falls back to lexical retrieval instead of blocking the player request while a complete corpus is embedded.

Knowledge articles live in the configured external `knowledge` directory. Keep them concise, attributable and player-safe. Never put credentials, staff-only notes, reports, sanctions, private player information or infrastructure details in knowledge files.

Bundled files listed in `knowledge/index.txt` are AIlex-managed reviewed knowledge and are refreshed from the plugin JAR on startup. Operator-authored knowledge must use separate filenames not listed in the manifest; those files are left untouched. Commands, Discord channel names, URLs, ranks, roles, warps, menu names and other exact server identifiers should be stored canonically and never translated into invented identifiers. If the exact identifier is not in trusted evidence, AIlex must abstain rather than guess.

Open-ended prompts such as “tell me a fun fact” use corpus discovery rather than forcing a meaningless lexical search.

## Bounded typed read-agent

```yaml
agent:
  enabled: true
  planner_model: "gpt-5.6-luna"
  max_tool_calls_per_round: 2
```

`AssistantToolRegistry` is the model-facing capability boundary. Each `AssistantTool` owns a strict schema, a deterministic availability predicate and a bounded Java executor. The planner can only choose from tools registered for the current request; it cannot execute commands, discover arbitrary plugin APIs, access SQL/filesystem internals or create new capabilities.

The agent is information-gain gated. Examples:

- complete live snapshot → no planner call;
- strong reviewed knowledge hit → no planner call;
- temporal memory wording → timeline lookup may be useful;
- missing server evidence → one focused knowledge search may be useful;
- a genuinely unresolved grounded request may use a second bounded read round when the first observation materially narrows the question.

Increasing tool rounds should be treated as a measured exception because it consumes both latency and final-answer call budget. Planner input/output token usage is tracked separately from final-generation usage.

## Read-only capability ceiling

`openai.assistant.tools.allowed` defines which source families may ever be exposed:

- `requester` — safe state about the requesting player (including bounded inventory state);
- `world` — dimension/biome/location/weather/time/light/target context;
- `nearby` — bounded nearby entity information;
- `server` — player-safe operational state such as online count/TPS/MSPT/version/uptime;
- `npc` — addressed bot state;
- `session` — dialogue, typed memory, timeline and verified experience;
- `knowledge` — reviewed HauntedMC knowledge.

`redact_other_players: true` should remain enabled. Other HauntedMC plugins should register an `AssistantContextProvider` for selected read-only feature state. Provider outputs are bounded and pass deterministic data-safety filters.

For grounded live-state work, AIlex freezes an authorized superset of safe Paper state on the server thread before asynchronous reasoning begins. The planner may inspect only that frozen snapshot; it never calls Bukkit/Paper asynchronously.

## Memory fabric

```yaml
memory:
  enabled: true
  max_shared_facts: 1024
  max_player_memories: 256
  max_context_characters: 10000
  shared_write_permission: "ailex.admin"
  storage:
    backend: "sqlite"
    shared_sync_seconds: 5
    mysql:
      jdbc_url: ""
      username: ""
      password: ""
      table_prefix: "ailex_"
```

`MemoryRecord` is the durable storage envelope. The cognitive memory model separates current/historical `MemoryClaim`s, typed `MemoryEvent`s, ordered `MemoryEpisode`s, relationship `MemoryEdge`s and verified procedural experience. Raw chat is not the durable identity model.

Memory candidates still pass source-support and privacy validation. Structured output being enabled does **not** mean every first-person sentence becomes permanent memory; it only gives the validator a candidate to evaluate.

Shared learned memory is fact-only and requires `shared_write_permission`. Player facts should remain player-scoped unless they are genuinely trusted server-wide facts.

### SQLite

`backend: sqlite` is self-contained and uses the embedded SQLite JDBC driver with WAL. This is the default development/single-runtime backend.

### Shared MySQL

`backend: mysql` makes the durable repository authoritative across AIlex runtimes. The MySQL JDBC driver is embedded in the plugin. Configure a normal JDBC URL plus credentials; these configuration values are never part of model context.

Each runtime serves player requests from its audience-indexed in-memory hot store. Database refresh runs off-thread and uses a database-owned monotonic change sequence, not server wall-clock timestamps, so clock skew and bursts do not silently skip changes. `shared_sync_seconds` controls refresh cadence; synchronization paginates until the runtime has consumed all available changes.

If MySQL is explicitly selected but unavailable at startup, AIlex deliberately uses a non-persistent in-memory fail-safe. It does **not** silently fall back to a per-server SQLite database, because doing so would create multiple divergent AIlex identities that later look authoritative. Production multi-runtime deployments should alert on `shared_memory=false` and restore the shared backend.

## Temporal truth

Corrections reuse stable semantic keys. A changed value receives a new validity start; the superseded row retains its previous validity interval and provenance. `MemoryTruthResolver` deterministically selects the best-supported claim for a requested point in time using source authority, confidence, validity, recency and salience. Near-tied conflicting values are represented as disputed.

This behavior is deterministic; there is no configuration that allows the model to override source authority or repository freshness.

## Chat and working context

`openai.chat.allow_implicit_followups` is `false` by default. With that default, every player-triggered assistant request must explicitly mention the bot name again; an active session only supplies context and does not claim ordinary player chat. Set it to `true` only when deliberately enabling natural no-mention follow-ups.

`openai.chat.session_timeout_seconds` controls how long an active player↔assistant conversation remains available as dialogue context. When implicit follow-ups are enabled, it also bounds how long no-mention continuation can be considered.

This direct-request boundary is separate from explicitly configured proactive behavior. Join/idle/community triggers may still let AIlex speak autonomously according to `openai.proactive_chat`; they are not treated as the player's direct request.

`openai.chat_context` controls short-lived raw conversation/ambient context. `persist_to_disk` is disabled by default. Durable knowledge belongs in typed memory instead of raw transcript persistence. The shipped character ceilings are 18,000 overall, 4,000 for ambient general chat, 8,000 for the active conversation and 5,000 for bot-memory context; message capture is capped at 900 characters.

Large `max_context_characters` values do not mean every message is copied into every prompt. `WorkingContextPolicy` decides when recent raw chat is useful.

## Proactive chat

All proactive work is lower priority than direct player requests.

### Public questions and social intervention

```yaml
questions:
  enabled: true
  probability: 0.3
  conversation_window_seconds: 45
  minimum_speaker_alternations: 2
  social_graph_window_seconds: 180
  strong_pair_score: 2.5
```

`SocialConversationGraph` is the single transient player-conversation model. It combines a bounded volatile speaker/message window for direct-address/alternation/thread evidence with decaying pair edges over the longer social window. Nothing in this graph is persisted, and it does not infer friendship, affection, personality or private social profiles.

A question must still look genuinely general/public. Likely player-to-player continuation is suppressed. Explicit broadcast wording such as `weet iemand...?`, `kan iemand...?`, `anyone know...?` or `can someone...?` can override conversation suppression because it clearly addresses the wider chat.

The policy is intentionally conservative: false silence is preferable to AIlex interrupting a human conversation.

### Join, collective and idle behavior

`join` controls occasional personal join greetings. `collective` reacts only after enough distinct players participate in configured community phrases. `idle` can produce rare low-priority activity after a long bot silence.

Use low probabilities and meaningful cooldowns. Proactive output should be exceptional, not a response to every chat signal.

## Evidence packets and claim-level verification

```yaml
verification:
  enabled: true
  minimum_confidence: "medium"
```

`EvidencePacket` normalizes the provenance IDs made available to deterministic grounding. Grounded structured output carries exact evidence IDs and line-level `claim_evidence`. IDs not supplied by reviewed knowledge, memory, the bounded read-agent or live snapshot are rejected. Once an answer cites evidence, every emitted line must carry a supporting mapping; partially grounded multi-line replies are invalid.

Server/live/memory routes require the appropriate evidence family even when retrieval returned nothing. Deterministic negative observations such as `knowledge.none`, `memory.none` or `live.*.none` let the model explain/abstain based on a real retrieval result; they do not authorize an unsupported positive claim. Failed verification may use a bounded stronger-model escalation when call/deadline budget remains; otherwise AIlex abstains with a safe fallback.

Plain-text FAST generation is an explicitly separate transport path. It must emit only player-facing prose; protocol/JSON envelopes are not valid chat. `AssistantReply` defensively unwraps recognized accidental envelopes and rejects unknown JSON objects so structured metadata cannot be displayed to players even if a model violates that contract.

## Reliability

- `circuit_breaker_enabled` prevents repeated upstream failure from occupying workers;
- static-answer caching is context-fingerprinted so changed memory/live evidence does not reuse stale output;
- direct requests use bounded concurrent/queued capacity;
- newer queued turns can supersede stale same-player work;
- proactive requests yield to direct traffic;
- embeddings and read tools fail toward simpler deterministic retrieval or explicit abstention rather than taking down chat;
- shared memory never performs database network I/O on the Paper tick thread;
- managed bundled knowledge is refreshed from the plugin JAR on startup;
- exact HauntedMC identifiers are evidence-gated and are not translated or guessed.

## Observability

`openai.assistant.observability` controls routing/completion diagnostics. Response previews are disabled by default. Prefer counters, route/model/tool usage and timing over logging player text or extracted memory values.

Useful commands include:

```text
/ailex ai status
/ailex ai usage
/ailex memory status
/ailex trace recent
```

`/ailex ai status` reports learned semantic retrieval/shared-memory state plus read-agent planner/tool counts and planner token totals. Per-request completion diagnostics include retrieved chunk count, evidence IDs, claim-evidence line count, model-call count and latency.

When tuning AIlex, measure response latency, model calls, tool calls, cached/input/output tokens, retrieval quality, fallback rate and queue pressure together. A change that improves one dimension by increasing every other cost is not automatically an improvement.
