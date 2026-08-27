# AIlex Architecture

This document describes the current cognitive assistant architecture. It is intentionally more technical than the main README; start there if you first want to understand what AIlex does from a player or server-owner perspective.

## The central design rule

AIlex separates **reasoning** from **authority**.

The language model is allowed to interpret natural language, decide which already-permitted information would be useful, combine evidence and propose a response. Deterministic Java code owns permissions, privacy, the model-facing capability boundary, memory acceptance, source precedence, answer verification and every server mutation.

That split is the reason AIlex can become more capable without giving an LLM arbitrary access to a Paper server.

## Request lifecycle

A direct request enters `AssistantChatController`, which records compact dialogue state and asks `AssistantService` to prepare the turn while Bukkit access is safe on the server thread.

The normal request path is:

1. `AssistantIntentClassifier` performs deterministic routing and language detection.
2. `RequiredContextPlanner` defines the initial information needs and capability-safe source plan.
3. `SemanticNeedPlanner` may refine ambiguous information needs without expanding permissions.
4. Safe live Minecraft state is copied into an immutable `LiveSnapshot`.
5. Relevant typed memory and reviewed knowledge are retrieved.
6. `AssistantReadAgent` may request additional read-only evidence if the initial evidence is insufficient.
7. `ContextCompiler` assembles a bounded prompt.
8. The configured model profile generates a structured reply.
9. `AssistantGroundingPolicy` and `AssistantEpistemicPolicy` validate evidence use and provenance.
10. Accepted memory candidates, verified procedural experience and approved physical action proposals pass through separate deterministic validators.
11. The response is delivered and observability counters/traces are updated.

The expensive path is not the default path. Complete deterministic evidence can skip model-driven information seeking entirely.

## Prompt architecture

`AssistantPromptComposer` keeps prompt responsibilities separate:

- a stable cognitive/epistemic contract that changes rarely and is suitable for provider-side prompt caching;
- small turn-specific instructions such as response language and chat-line budget;
- NPC/persona prompts that describe personality rather than duplicating safety, memory and evidence rules;
- tool-specific guidance inside the tool definitions themselves.

Structured Outputs is responsible for the reply shape. The system prompt therefore does not waste tokens repeatedly describing the entire JSON schema.

Prompt instructions are advisory; deterministic validation remains authoritative if the model ignores them.

## Context and token economy

AIlex does not expose every available source on every request. `ContextCompiler` allocates route-specific budgets to:

- the current player request;
- active dialogue;
- relevant durable memory;
- reviewed knowledge;
- selected live Minecraft state;
- read-tool observations when the bounded agent requested them.

Configured token counts are ceilings, not targets. Sources are ranked, clipped and deduplicated before prompt assembly. Planner input/output tokens are counted separately from final answer generation.

## Live Minecraft information

Paper state must be captured on the server thread. `AssistantLiveCapturePolicy` determines which safe source families may be frozen before asynchronous reasoning starts.

The safe live surface can include requester state, a bounded inventory/equipment summary, position/world/biome information, target state, nearby entity summaries, server health and NPC state. Trusted HauntedMC plugins can expose additional player-safe facts through `AssistantContextProvider`.

The provider API is an explicit integration boundary. It does not give the model reflection-based or arbitrary access to other plugins.

Sensitive infrastructure data, secrets, credentials, reports, sanctions and hidden staff information are rejected before model invocation.

## Reviewed knowledge

`LocalKnowledgeIndex` retrieves operator-maintained HauntedMC knowledge using several complementary signals:

- lexical/BM25-style relevance;
- exact command, title and alias matches;
- phrase and multilingual concept signals;
- learned semantic embeddings;
- reciprocal-rank fusion;
- source authority and freshness;
- expiry filtering;
- redundancy/diversity suppression.

Exact Minecraft commands remain strongly lexical because embeddings should not make `/claim` less precise. Semantic vectors help with paraphrases and meaning-level matches.

`KnowledgeDocumentParser` reads the Markdown front matter used by reviewed documents. Supported metadata includes stable evidence ID, aliases, category, authority, source, update date and expiry. Unknown or malformed metadata fails conservatively instead of silently becoming trusted provenance.

Corpus embeddings warm asynchronously. If the embedding endpoint is unavailable, lexical retrieval remains usable.

## Bounded read-agent

`AssistantTool` and `AssistantToolRegistry` define the complete model-facing read surface. The current tools cover:

- scoped durable memory search;
- memory timeline search;
- verified procedural-experience search;
- reviewed knowledge search;
- inspection of permitted families from the already-frozen live snapshot.

Each tool owns a strict schema, permission predicate and deterministic implementation. The planner cannot discover arbitrary Java methods, commands, filesystem paths, SQL access or plugin APIs.

`AssistantReadAgent` runs only when deterministic evidence appears insufficient. The loop has explicit model-call, tool-call and deadline budgets and suppresses equivalent duplicate calls. It is an information-acquisition controller, not an unrestricted autonomous agent.

## Epistemic policy and grounding

`AssistantEpistemicPolicy` distinguishes source families such as live runtime observations, reviewed knowledge, player/shared memory, event memory and negative lookup observations.

Source class and model confidence are deliberately different concepts. A confident model statement does not make weak evidence authoritative.

Important precedence rules include:

- current live runtime state is preferred for questions about current state;
- reviewed/operator-controlled knowledge outranks ordinary learned shared claims for HauntedMC facts;
- a player's current explicit statement about themself can supersede older player memory;
- historical questions use temporal/event memory rather than pretending the current value was always true;
- procedural experience can guide strategy but cannot ground a factual claim about the server or player.

`EvidencePacket` normalizes the evidence set available for validation. Grounded structured replies contain line-level evidence mappings. Unknown evidence IDs, wrong provenance families and negative-only evidence fail closed.

A retrieval miss is useful information for deciding to search again or abstain; it is not evidence that an invented answer is true.

## Long-term memory

`AssistantMemoryService` maintains a fast audience-indexed active view backed by a `MemoryRepository`. `MemoryRecord` remains the compact storage envelope, while higher-level code distinguishes different meanings:

- **claim memory** — a proposition AIlex currently or historically believes;
- **event memory** — something that happened at a particular time;
- **episode memory** — related events consolidated into a useful unit;
- **relationship memory** — factual player↔AIlex continuity and entity relations;
- **procedural experience** — verified lessons about retrieval, answering or action behavior.

Raw chat logs are not AIlex's long-term identity.

### Formation and correction

The model can propose a memory candidate, but Java validates scope, source support and privacy before storage. Shared learned memory is separately permission-gated.

Stable semantic keys let a correction create a new current value while retaining historical validity. Explicit forgetting removes the active meaning without rewriting history.

`MemoryTruthResolver` resolves the value appropriate to a point in time using deterministic validity/supersession and source metadata. Near-tied unresolved conflicts can remain disputed.

### Consolidation, maturation and retention

`AssistantMemoryConsolidator` converts eligible related event material into compact episodes without asking an LLM to rewrite every conversation.

`MemoryLifecycleStage` and `MemoryRetentionPolicy` model lifecycle/retention separately from factual authority. Important repeated memory can mature; weak competing memory can decay through interference-aware retention. Retrieval alone does not increase factual confidence.

Verified successful use may trigger reconsolidation, which can increase accessibility/salience while leaving factual authority unchanged.

### Associative recall

`MemoryGraphRetriever` adds graph activation to normal relevance scoring. It uses evidence-backed memory relations to surface related material and fuses that signal with lexical relevance, salience, confidence and recency rather than replacing them.

`MemoryTopicView` provides compact topic-structured context so a reasoning turn can receive coherent related memory without replaying the full durable store.

## Shared network identity

SQLite/WAL is the self-contained single-runtime backend. MySQL is the shared backend for deployments where one AIlex identity must persist across several Paper runtimes.

Player-facing reads are served from the hot in-memory view. Shared synchronization runs off-thread and uses a database-owned monotonic change sequence, including tombstones, rather than server wall clocks.

If MySQL is explicitly configured but unavailable at startup, AIlex uses a non-persistent fail-safe rather than silently creating independent authoritative SQLite histories on each server.

## Verified experience learning

`AssistantExperienceMemoryService` stores strategy lessons only when an external/deterministic outcome supports them. Examples include accepted or rejected grounding, a verified correction, a successful tool path, a failed retrieval route or a deterministically validated physical action outcome.

The model cannot write “I think I did badly” and have that become a durable lesson. Self-criticism without evidence is not learning.

Experience remains NPC/strategy scoped and is not player-facing factual evidence.

## Dialogue, relationships and social participation

Immediate dialogue is kept separately from long-term memory so natural follow-ups do not require full transcript replay.

`AssistantRelationshipMemoryService` builds a conservative longitudinal relationship profile from explicit or observed information such as interaction count, language preference, stated interests, goals and shared episodes. It intentionally avoids inferred affection, personality, mental state or other psychological profiling.

`SocialConversationGraph` is a volatile public-chat model. It tracks recent speakers, directed reply cues, short-lived pair edges, topic/thread context and recent AIlex interventions. It is not persisted as a friendship graph.

`ProactiveInterventionPolicy` and `ProactiveGoalService` decide whether AIlex should participate under bounded goals such as welcoming, helping a new player, celebrating, supporting a public conversation, connecting players who explicitly ask for company, defusing non-moderation conflict, following up on an explicit goal or informing the community.

`SILENCE` is a first-class successful outcome. The policy subtracts privacy/intrusion, error and repetition costs rather than optimizing only for response frequency.

Follow-ups are private to the relevant player and derive only from explicit non-sensitive goals.

## Controlled physical actions

The language model may propose only the small configured action set represented by `AssistantActionProposal`. `AssistantActionService` then re-validates the player's explicit wording, allowed action type, requester identity and live NPC/world state before queuing anything.

The current physical actions are deliberately narrow: follow the requester, come to the requester and stop moving.

`AssistantActionOutcomeRecorder` records the deterministic execution result as an event and may derive verified procedural experience from that outcome. This creates a bounded action→outcome→learning loop without allowing the model to declare its own action successful.

## Evaluation

AIlex uses deterministic CI for the control plane. It must not depend on live stochastic model output.

The test suite covers routing, prompt invariants, hybrid retrieval, front-matter provenance, temporal memory, correction/forgetting, graph recall, consolidation, maturation/retention, typed tools, epistemic evidence, claim-level grounding, privacy, shared synchronization, social intervention and controlled actions.

`AIlexBench` is the scenario-oriented cognition regression layer. It is intended to test behavior across components—especially memory updates, temporal reasoning, abstention and memory→tool/action use—rather than just isolated utility functions.

See [Testing and Quality](TESTING.md) for exact commands and expected coverage.

## Safety boundary summary

The model may generate text, request explicitly registered read tools, propose memory candidates and propose a small configured physical action. It cannot directly:

- execute arbitrary commands;
- mutate the world/economy/moderation state;
- write arbitrary database rows or files;
- inspect arbitrary plugins or configuration;
- access credentials/infrastructure internals;
- read private other-player state;
- bypass memory validation;
- bypass evidence validation;
- bypass deterministic physical-action approval.

Adding a new capability therefore means adding a new explicit typed boundary and tests, not merely telling the model that it has another tool.
