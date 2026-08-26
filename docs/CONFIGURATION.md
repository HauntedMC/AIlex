# Configuration

`src/main/resources/config.yml` is the source of truth for shipped settings. Keep production overrides small and prefer the adaptive defaults unless measurements show a reason to change them.

## API and inference

`openai.api_key` configures the provider credential. `store_responses` is disabled by default. `request_timeout_seconds` bounds upstream calls.

The assistant uses three adaptive profiles under `openai.assistant.models`:

- `fast` for ordinary conversation;
- `grounded` for factual, live-state and evidence-backed work;
- `deliberate` for expensive reasoning/escalation.

`max_model_calls`, `max_tool_rounds` and `total_deadline_seconds` bound work per request. These limits exist to protect the Minecraft server from unbounded retries and latency.

## Context budgets

`openai.assistant.context` defines hard prompt ceilings:

```yaml
context:
  max_input_tokens_fast: 3000
  max_input_tokens_grounded: 9000
  max_input_tokens_deliberate: 18000
```

They are ceilings, not target prompt sizes. `ContextCompiler` prioritizes the current request and useful evidence, then allocates space to dialogue, live state, memory, reviewed knowledge and optional recent chat. Increasing these values only helps when useful context is available; it should not be used as a substitute for better retrieval.

## Knowledge retrieval

`openai.assistant.retrieval` controls reviewed-knowledge selection:

```yaml
retrieval:
  hybrid_enabled: true
  max_chunks: 10
  max_evidence_characters: 24000
  query_cache_seconds: 300
  exclude_expired: true
```

Knowledge articles live in the configured external `knowledge` directory. Keep them concise, sourceable and player-safe. Never put credentials, staff-only notes, reports, sanctions, private player information or infrastructure details in knowledge files.

Open-ended prompts such as “tell me a fun fact” use corpus discovery instead of requiring strong lexical query terms.

## Read-only live tools

`openai.assistant.tools.allowed` is a capability ceiling. The deterministic context planner chooses the relevant subset for each turn.

Typical sources are:

- `requester` — safe state about the player asking;
- `world` — world, biome, location, weather, time, light, target context;
- `nearby` — bounded nearby entity information;
- `server` — safe operational state such as online count/TPS/MSPT/version/uptime;
- `npc` — the addressed bot's safe state;
- `session` — dialogue and typed memory;
- `knowledge` — reviewed HauntedMC knowledge.

`redact_other_players: true` should remain enabled unless there is a specific player-facing reason to expose other player names.

Other HauntedMC plugins should use `AssistantContextProviderRegistry` to expose additional read-only facts. A provider should return only information that is safe and useful for the requesting player.

## Durable memory

```yaml
memory:
  enabled: true
  max_shared_facts: 1024
  max_player_memories: 256
  max_context_characters: 8000
  shared_write_permission: "ailex.admin"
```

Durable memory is typed rather than transcript-based. Supported semantic kinds are facts, preferences, opinions, interests and goals; relationship/event memory is written by trusted runtime code.

Shared learned memory is limited to facts and requires `shared_write_permission`. Do not make this permission globally available: player statements about themselves should remain player-scoped, while server-wide facts need a trusted author.

Goals are treated as temporary current projects and expire unless reconfirmed. Corrections reuse a stable semantic key and supersede the old active meaning. Explicit forgetting removes the named player semantic key.

The SQLite database is local to the plugin and WAL is enabled for low-overhead durable writes.

## Chat and working context

`openai.chat.session_timeout_seconds` controls how long a player can naturally continue an active player↔assistant conversation without repeating the assistant name.

`openai.chat_context` controls short-lived raw conversation/ambient context. This data is not the assistant's durable identity store. `persist_to_disk` is disabled by default; durable knowledge should be represented through typed memory instead.

Large `max_context_characters` values do not mean every message is copied into every prompt. `WorkingContextPolicy` decides whether historical raw chat is useful for the current turn.

## Proactive chat

All proactive activity is lower priority than a direct player request.

### Public questions

```yaml
questions:
  enabled: true
  probability: 0.3
  conversation_window_seconds: 45
  minimum_speaker_alternations: 2
```

AIlex keeps a small in-memory recent-speaker window. Questions are suppressed when recent alternation/direct-address/contextual wording indicates an active player-to-player conversation. Explicit public cues such as `weet iemand...?`, `kan iemand...?`, `anyone know...?` or `can someone...?` remain eligible.

The tracker is deliberately conservative: missing an occasional proactive answer is better than interrupting two players talking to each other.

### Join, collective and idle behavior

`join` controls occasional personal join greetings. `collective` reacts only after enough distinct players participate in configured community phrases. `idle` can produce rare low-priority activity after a long bot silence.

Use low probabilities and meaningful cooldowns. Proactive AI should feel like a participant, not a chat bot responding to everything.

## Verification

```yaml
verification:
  enabled: true
  minimum_confidence: "medium"
```

For grounded server/live questions, AIlex validates that model-supplied evidence IDs actually exist in the request. Answers that require local/live grounding cannot silently cite invented source identifiers.

## Reliability

- `circuit_breaker_enabled` prevents repeated upstream failure from occupying workers.
- static answer caching is context-fingerprinted so changed memory/live evidence does not reuse a stale grounded answer.
- direct requests use bounded concurrent/queued capacity.
- proactive requests yield to direct traffic.

## Observability

`openai.assistant.observability` controls routing/completion diagnostics. Response previews are disabled by default. Prefer structured operational metadata over logging player chat or extracted memory values.

Useful commands include:

```text
/ailex ai status
/ailex ai usage
/ailex memory status
/ailex trace recent
```

When tuning AIlex, measure latency, model calls, cached/input/output tokens, retrieval counts, fallback rate and queue pressure together. Optimizing only one of these usually moves cost or latency somewhere else.
