# AIlex 1.9.0 — Chat Intelligence

AIlex 1.9.0 is a player-facing chat intelligence release built on the 1.8.2 liveness fixes. It deliberately keeps the existing deterministic safety, provenance and durable-memory architecture while improving the orchestration that players actually experience.

## Main changes

- Terra is the normal player-facing model tier; Luna remains for cheap planning/background work and Sol for difficult deliberate escalation.
- Player answers are no longer forced into one line. Delivery remains bounded but adapts to the amount of explanation actually needed.
- Active dialogue is replayed through real Responses API `user`/`assistant` roles instead of being flattened into one user prompt.
- Working conversation now has a verbatim recent-turn window plus a bounded mid-term digest/topic state; durable player memory remains separate.
- Context compilation reserves budget for useful current/trusted sources and avoids treating a larger context ceiling as a quality target.
- Hybrid knowledge retrieval now has a deterministic adaptive evidence-selection stage: clear/high-confidence questions use smaller evidence sets while ambiguous questions preserve recall.
- A reviewed canonical identifier registry covers exact HauntedMC Discord channels, ranks, active game modes and known commands. Complete identifier kinds can support safe negative answers.
- Physical actions are disabled by default and removed from ordinary FAST-chat structured-output triggering.
- `docs/CHAT_EVALUATION.md` defines separate quality metrics for correctness, grounding, retrieval, identifiers, continuity, memory updates, naturalness, latency, cost and liveness.

## Research basis

The README links every research idea that is actually reflected in the implementation. 1.9.0 specifically adds adaptations inspired by Adaptive-RAG, Lost in the Middle, Reflective Memory Management, MemoryOS, RAG knowledge-selection research and RAGChecker, while retaining earlier Hindsight/HippoRAG 2/LongMemEval-inspired mechanisms. AIlex does not claim paper-equivalent implementations or benchmark scores.
