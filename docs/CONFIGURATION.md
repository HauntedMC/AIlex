# Configuration Guide

This guide focuses on practical setup and safe operation of AIlex.

## Runtime File Layout

AIlex stores files in your Paper plugin data directory:

- `plugins/AIlex/config.yml`: movement/action tuning and OpenAI settings.
- `plugins/AIlex/data.yml`: persisted NPC data (id, name, spawn, implementation class).

## Core Config Keys

Top-level sections in `config.yml`:

- `openai.api_key`: API key for chat responses. Keep empty if not used.
- `openai.model`: OpenAI Responses API model identifier used by `ChatGPTClient`.
- `npc.general.maxVelocity`: global NPC movement speed cap.
- `npc.general.maxRotation`: global angular speed cap.
- `npc.behaviour.*`: per-behaviour parameters (acceleration, slow radius, prediction time, wander tuning).
- `npc.action.*`: stop thresholds for command actions (`movehere`, `followplayer`, `fleeplayer`, `mirrorplayer`).

## Safe Change Workflow

1. Back up `config.yml` before larger tuning changes.
2. Change one behavior group at a time (e.g., only `arrive`).
3. Run `/ailex reload` to apply updates.
4. Validate NPC behavior with debug-visible scenarios.

## OpenAI Integration Notes

- Leave `openai.api_key` empty when LLM chat replies are not needed.
- Pick an active chat-capable model (for example `gpt-4.1-mini`) in `openai.model`.
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
