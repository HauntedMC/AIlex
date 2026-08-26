# Configuration Guide

This guide focuses on practical setup and safe operation of AIlex.

## Runtime File Layout

AIlex stores files in your Paper plugin data directory:

- `plugins/AIlex/config.yml`: movement/action tuning and OpenAI settings.
- `plugins/AIlex/data.yml`: persisted NPC data including per-entity behavior/display properties.

## Core Config Keys

Top-level sections in `config.yml`:

- `openai.api_key`: API key for chat responses. Keep empty if not used.
- `openai.model`: fallback OpenAI Responses API model for legacy/direct chat paths.
- `openai.max_output_tokens`: hard cap for a chat reply; `120` is appropriate for one Minecraft chat line.
- `openai.reasoning_effort`: fallback thinking budget (`none`, `low`, `medium`, `high`, `xhigh`, or `max`).
- `openai.store_responses`: API-side response retention; leave this `false` for player chat by default.
- `openai.request_timeout_seconds`: upstream-response deadline (3–60 seconds).
- `openai.chat`: access, response-routing, and in-flight request controls. `requester` visibility is the safest default; use `server` only for deliberately public NPCs.
- `openai.chat.standalone`: the mention, display name, and persona for chat-only mode. It is used when
  `npc.enabled: false`, without creating a Citizens NPC.
- `openai.rate_limit.feedback`: private, configurable feedback for players who reach the response limit. Its message supports `{remaining_seconds}`.
- `openai.rate_limit.bypass_permission`: staff permission that bypasses the per-player AI response limit. The
  default is `ailex.rate_limit.bypass`, which defaults to operators; grant it to other staff roles as needed. Leave
  it empty to disable the bypass.
- `openai.safety.enabled`: enables a mandatory global safety system prompt.
- `openai.safety.system_prompt`: non-optional safety policy prompt prepended to every OpenAI request.
- `openai.knowledge`: an operator-maintained bullet-point knowledge base. AIlex selects relevant bullets for each question before applying `max_characters`.
- `openai.knowledge.external`: reviewed Markdown/text facts from `plugins/AIlex/knowledge/`. This is the right place for maintainable server documentation; do not put private staff or player data in it because selected text is sent to the model.
  AIlex ships reviewed topic guides for core HauntedMC information; they are copied into this directory on first startup.
  The shipped `README.md` is only an authoring guide and is never indexed as evidence.
- `openai.chat_context`: bounded recent chat context. Its per-source and total character limits are input-cost controls.
  With `persist_to_disk: true` (the default), it is restored from
  `plugins/AIlex/assistant-short-term-memory.yml` after reloads or restarts. The file includes recent chat, per-NPC
  conversation context, bot memory, and inspectable live metadata snapshots. Metadata is never reused as live state:
  every new request obtains a fresh snapshot. Set it to `false` and restart the plugin to start with an empty,
  in-memory context.
- `openai.chat_context.metadata.only_when_relevant`: sends live Minecraft metadata only for questions where location, inventory, nearby entities, or player state can help.
- `openai.assistant`: enables the adaptive pipeline. It routes casual chat to a fast response, retrieves local
  knowledge for server facts, and captures a small read-only Paper/Bukkit snapshot for live questions. Stable
  vanilla gameplay questions can use the model's general Minecraft knowledge by default.
- `openai.assistant.routing.default_language` and `allowed_languages`: control player-facing language. The default is
  Dutch (`nl`); AIlex detects English only when `en` is allowed, and falls back to Dutch for ambiguous or unsupported input.
- `openai.assistant.models.fast|grounded|deliberate`: independent model, reasoning, and output-token profiles.
  The shipped configuration uses Luna/low for casual chat, Terra/medium for grounded server facts, and Sol/high only
  for deliberate live or support requests. Adjust these based on measured latency, quality, and cost for your server.
- `openai.assistant.retrieval`: controls the local knowledge index and the evidence budget. Knowledge lives in
  `knowledge/*.md`; expired articles are excluded when configured.
- `openai.assistant.structured_output`: requires a small JSON reply contract before AIlex renders player-facing chat.
  AIlex rejects malformed or ungrounded factual replies instead of broadcasting them.
- `openai.assistant.verification`: validates the reply contract and confidence. Local knowledge and live Bukkit
  snapshots enrich answers, but do not prevent the model from using its general knowledge.
- `openai.assistant.memory`: automatically stores only explicit, non-sensitive preferences and durable facts; it
  never stores chat transcripts. `assistant-memory.yml` stores per-player preferences, while
  `assistant-long-term-memory.yml` stores shared server facts (such as public staff roles) and player facts. Each
  accepted memory is written immediately with an atomic file replacement, so it survives plugin reloads and normal
  server restarts without leaving a partially written YAML file.
  Harmless personal interests and repeatedly mentioned topics are eligible for player memory; repeated-topic
  detection is session-only and stores no chat transcript.
  Both files are system-managed and can be overwritten; put maintained documentation in `openai.knowledge` or
  `knowledge/*.md` instead. Shared facts are automatically written only by players with
  `openai.assistant.memory.shared_write_permission` (default: `ailex.admin`); leave that setting empty to allow
  every player to contribute shared facts.
- `openai.assistant.observability`: emits a route and completion line for every assistant request. It records the
  selected intent, layer, language, model, retrieval/evidence result, response classification, and latency without
  logging prompts or chat. Set `include_response_preview: true` only when administrators need short answer previews.
- `npc.defaults.entity.*`: default entity properties used when creating new NPCs.
- `npc.enabled`: controls physical Citizens NPCs. Set it to `false` to skip loading and creation of all AIlex NPCs
  while retaining mention-based assistant chat through `openai.chat.standalone`.
- `npc.general.maxVelocity`: global NPC movement speed cap.
- `npc.general.maxRotation`: global angular speed cap.
- `npc.behaviour.*`: per-behaviour parameters (acceleration, slow radius, prediction time, wander tuning).
- `npc.action.*`: stop thresholds for command actions (`movehere`, `followplayer`, `fleeplayer`, `mirrorplayer`).

## Entity Defaults

`npc.defaults.entity.*` in `config.yml` controls defaults for newly created `/ailex create` NPCs:

- `prefix`: displayed before NPC name (nameplate + chat response prefix).
- `tabPrefix`: optional symbol/text before prefix in tab list.
- `tabListOrder`: specific tab order value (lower value generally pushes entries down).
- `damageable`: whether players/world can damage the NPC.
- `respawnOnDeath`: whether NPC auto-respawns after death.
- `chatEnabled`: whether mention-based AI chat replies are enabled for this NPC.
- `listedInTab`: whether NPC should appear in tab list.
- `alwaysUseNameHologram`: Citizens name-hologram behavior.
- `prompts.systemPrompt`: per-NPC system prompt used for LLM behavior/persona.
- `prompts.userPromptTemplate`: per-NPC user prompt template.

Supported placeholders in `prompts.userPromptTemplate`:

- `{player_name}`
- `{player_display_name}`
- `{npc_name}`
- `{npc_display_name}`
- `{chat_message}`

## Per-NPC Data Schema

Each NPC in `data.yml` now stores entity properties under:

- `npcs.<id>.entity.name`
- `npcs.<id>.entity.properties.prefix`
- `npcs.<id>.entity.properties.tabPrefix`
- `npcs.<id>.entity.properties.tabListOrder`
- `npcs.<id>.entity.properties.damageable`
- `npcs.<id>.entity.properties.respawnOnDeath`
- `npcs.<id>.entity.properties.chatEnabled`
- `npcs.<id>.entity.properties.listedInTab`
- `npcs.<id>.entity.properties.alwaysUseNameHologram`
- `npcs.<id>.entity.properties.prompts.systemPrompt`
- `npcs.<id>.entity.properties.prompts.userPromptTemplate`

## Safe Change Workflow

1. Back up `config.yml` before larger tuning changes.
2. Change one behavior group at a time (e.g., only `arrive`).
3. Run `/ailex reload` to apply updates.
4. Validate NPC behavior with debug-visible scenarios.

## OpenAI Integration Notes

- Leave `openai.api_key` empty when LLM chat replies are not needed.
- Configure active models in `openai.assistant.models.*`; `openai.model` remains the fallback for direct chat paths.
- Keep `openai.safety.enabled: true` for production/public servers.
- Rotate keys immediately if a key was ever committed publicly.
- Keep prompts/responses short to reduce latency impact.
- Prefer concise, independently useful knowledge bullets; the retrieval step matches the player's words and commands against them.

## Operational Safety Notes

- Avoid extreme acceleration/rotation values that cause jitter.
- Keep action stop distances realistic to avoid oscillation.
- Verify plugin dependencies (`Citizens`, `packetevents`) are present before startup.
- `/ailex` management commands require `ailex.admin` (operators have it by default). Do not grant it to normal players.
- A player can have only one active AI request and the global `max_concurrent_requests` cap prevents an API outage or chat burst from consuming unbounded threads.
- The assistant tool boundary is read-only. It can inspect the addressed player's current Paper/Bukkit context, but it
  never executes commands, changes NPC behavior, accesses other plugins, or performs economy/moderation actions.
- NPC names are matched as words, so a bot called `Alex` does not trigger on `Alexander`. Players can use `@Alex` or `Alex` naturally.
- AIlex only initializes, resets, respawns, or removes Citizens NPCs carrying its own metadata; matching names alone
  are never treated as ownership.
- The bot is an assistant, not an enforcement system: never let it directly execute moderation, economy, ban, rollback, whitelist, or console commands. Route those actions through reviewed staff workflows with explicit permissions and audit records.

## Troubleshooting Tips

- Missing NPCs after restart: verify `data.yml` and class names in stored entries.
- NPCs not reacting: verify current movement behaviour and world/entity conditions.
- LLM replies missing: verify `openai.api_key`, model value, and outbound network access.
