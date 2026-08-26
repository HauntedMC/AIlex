# Configuration Guide — AIlex 1.5

This guide covers the production assistant settings introduced or materially changed in AIlex 1.5. For upgrades from 1.4.x, read [MIGRATION-1.5.md](MIGRATION-1.5.md) first.

## Runtime files

AIlex uses the Paper plugin data directory:

- `config.yml` — runtime configuration (`config_version: 2`).
- `data.yml` — persisted NPC definitions/properties.
- `assistant-memory.db` — typed Memory V2 SQLite database.
- `.assistant-memory-v2-migrated` — one-time legacy memory migration marker when applicable.
- `knowledge/*.md|*.txt` — reviewed server knowledge.

Old `assistant-memory.yml` and `assistant-long-term-memory.yml` files are migration inputs only. AIlex 1.5 does not recreate them. `assistant-short-term-memory.yml` is no longer bundled.

## OpenAI defaults

```yaml
openai:
  api_key: ""
  model: "gpt-5.6-luna"
  max_output_tokens: 120
  reasoning_effort: "low"
  store_responses: false
  request_timeout_seconds: 20
```

`openai.model` is the fallback for direct/legacy paths. The adaptive assistant uses the independent profiles under `openai.assistant.models`.

Keep `store_responses: false` for normal player chat unless there is a deliberate retention requirement.

## Adaptive assistant

```yaml
openai:
  assistant:
    enabled: true
    mode: "adaptive"
    total_deadline_seconds: 15
    max_model_calls: 3
    max_tool_rounds: 2
    structured_output: true
```

Adaptive routing keeps ordinary conversation cheap while allowing stronger profiles for work that benefits from them.

### Model profiles

```yaml
models:
  fast:
    model: "gpt-5.6-luna"
    reasoning_effort: "low"
    max_output_tokens: 96
  grounded:
    model: "gpt-5.6-terra"
    reasoning_effort: "medium"
    max_output_tokens: 220
  deliberate:
    model: "gpt-5.6-sol"
    reasoning_effort: "high"
    max_output_tokens: 360
```

Ordinary fast chat normally uses the plain-text path. Structured output is used when verification/memory/evidence semantics justify the extra work. Grounded work may perform a single bounded escalation to the deliberate profile if the first result is unacceptable and deadline/model-call budget remains.

### Input budgets

```yaml
context:
  max_input_tokens_fast: 1000
  max_input_tokens_grounded: 2800
  max_input_tokens_deliberate: 4800
```

These are ceilings, not targets. `RequiredContextPlanner` first selects the smallest useful source set; `ContextCompiler` then enforces the route budget.

Increasing these values should be based on real production misses, not on the assumption that more context makes the model smarter.

## Routing and language

```yaml
routing:
  default_language: "nl"
  allowed_languages: ["nl", "en", "de"]
  language_detection: true
  clarify_only_when_required: true
```

Active dialogue state is considered when routing short follow-ups so players do not have to repeat the NPC name or entire question on every turn.

## Read-only live context

```yaml
tools:
  read_only: true
  allowed: ["knowledge", "requester", "world", "nearby", "server", "npc", "session"]
  redact_other_players: true
```

The allowed list is a capability ceiling; it does **not** mean every source is included in every prompt. The context planner selects only what the current intent/message needs.

Typical behavior:

- casual conversation — no live snapshot;
- held-item/health question — requester source;
- location/weather question — world source;
- nearby-player/entity question — nearby source without automatically adding global server state;
- online-count/TPS/server-version question — server source;
- NPC-position question — NPC source;
- event recall — typed event memory, not a full current-world dump.

The assistant does not execute commands or mutate Minecraft state.

## Knowledge retrieval

```yaml
retrieval:
  hybrid_enabled: true
  max_chunks: 5
  max_evidence_characters: 6500
  query_cache_seconds: 300
  exclude_expired: true
```

Hybrid retrieval is local. Ranking combines lexical BM25, title/command aliases, phrase matching, multilingual concept expansion and a hashed dense signal. Near-duplicate evidence is suppressed before prompt assembly.

Maintained HauntedMC facts belong in `knowledge/*.md` or `.txt`. Never place API keys, private staff notes, player reports, sanctions or personal information in knowledge files because selected evidence is sent to the model.

General Minecraft knowledge remains available when no local HauntedMC article is required.

## Memory V2

```yaml
memory:
  enabled: true
  retention_days: 90
  max_shared_facts: 128
  max_player_facts: 24
  shared_write_permission: "ailex.admin"
```

Durable memory is stored in `assistant-memory.db` with typed scope/kind, provenance, confidence, salience, timestamps, expiry and supersession metadata.

Memory categories include preferences, player/shared facts, factual player↔NPC relationship state, episodic memories and selected events. The service rejects sensitive/invented candidates before persistence.

`shared_write_permission` controls who may teach server-wide shared memory. Reviewed stable server documentation should still go in `knowledge/`, not shared player memory.

### Event memory

Built-in selective event recording includes session transitions, world/gamemode changes, deaths and advancements. Integrations can call `AssistantEventMemoryService.recordCustomEvent(...)` for meaningful HauntedMC events.

Do not turn every Bukkit event into memory. High-volume movement, damage ticks and block events belong in live state or nowhere, not long-term assistant memory.

## Raw chat context

```yaml
chat_context:
  enabled: true
  persist_to_disk: false
```

This is intentionally different from Memory V2.

`ChatContextStore` is a bounded short-term fallback for recent server/NPC chat. In config version 2 its disk persistence is **off by default**. The 1.4→1.5 migration also turns the old default off once.

If an operator explicitly sets `persist_to_disk: true` after the config is already version 2, AIlex preserves that opt-in. Durable facts/preferences/events should still use Memory V2 rather than raw transcripts.

## Request reliability

```yaml
chat:
  max_concurrent_requests: 4
  max_queued_requests: 8
  session_timeout_seconds: 60
```

AIlex 1.5 uses bounded priority admission:

- direct player requests are admitted first;
- active-session follow-ups are retained rather than silently discarded;
- the newest queued turn for one busy player supersedes their older queued turn;
- proactive traffic is rejected first and never consumes direct queue capacity;
- when the queue is truly full the player receives configured busy feedback.

Player-facing feedback lives under `openai.chat.feedback`.

## Response visibility

`openai.chat.response_visibility` supports the project’s configured visibility modes. Use requester/private visibility when the response may depend on player-specific context. Global/server visibility is appropriate only for intentionally public bots.

`nearby_response_radius` controls nearby delivery when that mode is selected.

## Rate limiting

```yaml
rate_limit:
  enabled: true
  max_responses_per_player: 10
  window_seconds: 600
  bypass_permission: "ailex.rate_limit.bypass"
  bypass_operators: true
```

The rate limiter is independent from request admission. It controls player consumption over time; admission controls instantaneous concurrency/queue pressure.

## Safety

Keep `openai.safety.enabled: true` for a public server. The global safety instructions are prepended independently from NPC persona prompts and player content cannot override them.

The assistant is not a moderation execution engine. Reports, punishments, purchases, refunds, account recovery, economy mutations and console commands must remain in explicit reviewed server workflows.

## Observability and diagnostics

```yaml
observability:
  enabled: true
  include_requester_name: true
  include_response_preview: false
  max_response_preview_characters: 240
```

Normal diagnostic logs record routing/model/outcome/latency and context source counts without prompt text. Response previews are opt-in.

Useful commands:

- `/ailex ai status` — assistant counters, active traces and cumulative provider usage.
- `/ailex ai usage` — OpenAI calls, success count, input/cached/cache-write/output tokens and cache-hit ratio.
- `/ailex trace recent [player] [limit]` — completed/rejected/superseded/upstream-failed request traces.
- `/ailex memory status` — typed active-memory counts.
- `/ailex memory recent` — recent memory metadata.
- `/ailex ai rebuild-index` — reload knowledge and memory indexes.

Provider token counters reset when the OpenAI client is recreated (for example by `/ailex reload`). Treat them as a runtime-window diagnostic, not durable billing records.

## NPC settings

`npc.enabled: false` disables physical Citizens NPC creation/loading while allowing the configured standalone assistant mode.

`npc.defaults.entity.*` controls defaults for newly created NPCs, including prefix/tab display, damageability, respawn behavior, chat enablement and persona/user-prompt templates.

Supported user-prompt placeholders include:

- `{player_name}`
- `{player_display_name}`
- `{npc_name}`
- `{npc_display_name}`
- `{chat_message}`

Movement/action tuning remains under `npc.general`, `npc.behaviour.*` and `npc.action.*`.

## Recommended production workflow

1. Back up `plugins/AIlex/` before upgrading or making large config changes.
2. Keep `store_responses: false`, `chat_context.persist_to_disk: false` and safety enabled unless there is a deliberate reason otherwise.
3. Start with shipped route/model/token budgets.
4. Validate normal chat, follow-ups, live state, server facts and event/memory recall.
5. Use `/ailex trace recent` for reliability failures and `/ailex ai usage` for token/cache behavior.
6. Change one budget/model/retrieval setting at a time and compare actual behavior.
7. Run `/ailex reload` after config changes.

For the internal component flow and design boundaries, see [ARCHITECTURE.md](ARCHITECTURE.md).
