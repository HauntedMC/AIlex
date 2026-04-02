# Configuration Guide

This guide focuses on practical setup and safe operation of AIlex.

## Runtime File Layout

AIlex stores files in your Paper plugin data directory:

- `plugins/AIlex/config.yml`: movement/action tuning and OpenAI settings.
- `plugins/AIlex/data.yml`: persisted NPC data including per-entity behavior/display properties.

## Core Config Keys

Top-level sections in `config.yml`:

- `openai.api_key`: API key for chat responses. Keep empty if not used.
- `openai.model`: OpenAI Responses API model identifier used by `ChatGPTClient`.
- `openai.safety.enabled`: enables a mandatory global safety system prompt.
- `openai.safety.system_prompt`: non-optional safety policy prompt prepended to every OpenAI request.
- `npc.defaults.entity.*`: default entity properties used when creating new NPCs.
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
- Pick an active chat-capable model (for example `gpt-4.1-mini`) in `openai.model`.
- Keep `openai.safety.enabled: true` for production/public servers.
- Rotate keys immediately if a key was ever committed publicly.
- Keep prompts/responses short to reduce latency impact.

## Operational Safety Notes

- Avoid extreme acceleration/rotation values that cause jitter.
- Keep action stop distances realistic to avoid oscillation.
- Verify plugin dependencies (`Citizens`, `packetevents`) are present before startup.

## Troubleshooting Tips

- Missing NPCs after restart: verify `data.yml` and class names in stored entries.
- NPCs not reacting: verify current movement behaviour and world/entity conditions.
- LLM replies missing: verify `openai.api_key`, model value, and outbound network access.
