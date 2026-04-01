# Architecture Overview

AIlex is a Paper plugin for AI-driven NPC control with three central domains: NPC lifecycle, movement behaviours, and action execution.

## Design Goals

- Keep NPC runtime behavior modular and pluggable.
- Separate action logic from low-level kinematic updates.
- Keep command-triggered behavior explicit and debuggable.
- Support optional LLM interaction without coupling core movement systems to network calls.

## Core Components

- `AIlexPlugin`: plugin bootstrap/enable/disable lifecycle and registration.
- `NPCHandler`: registry, persistence integration, and spawn/remove orchestration.
- `NPC` and `AilexNPC`: base kinematic state and concrete movement integration.
- `MovementBehaviour` implementations: seek/flee/arrive/align/face/lookvelocity/pursue/evade/wander.
- `MoveAction` implementations: command-triggered action primitives (`movehere`, `followplayer`, etc.).
- `DataHandler` and `ConfigHandler`: persistent NPC storage and runtime tuning config.
- `LLMChatListener` + `ChatGPTClient`: optional chat-to-AI forwarding and response generation.

## Runtime Flow

Startup:

1. Config/data handlers and chat client are initialized.
2. Commands and listeners are registered.
3. Saved NPCs are loaded from `data.yml` and spawned.

Command flow:

1. `/ailex` parses subcommands and validates target IDs/types.
2. Actions and movement behaviours are resolved from reflection-registered maps.
3. Actions are queued on NPCs and executed with priority ordering.

Movement loop:

1. Active action computes a target or condition.
2. Active movement behaviour produces a `MovementRequest`.
3. NPC kinematics are updated and applied to the in-world fake player entity.

## Integration Boundaries

- Citizens provides NPC entity lifecycle.
- PacketEvents provides tab-list packet updates for player visibility.
- Paper/Bukkit scheduler and event APIs drive execution and listeners.
- OpenAI HTTP calls are isolated to `ChatGPTClient`.

## Why This Matters

For operators, this structure keeps tuning mostly in `config.yml` while preserving clear operational commands.

For contributors, it keeps behavior boundaries clear: actions, movement algorithms, and lifecycle wiring are separate change surfaces.
