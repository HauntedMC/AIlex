# AIlex Architecture

AIlex is a read-only AI assistant runtime around Paper and Citizens. Deterministic server code decides when AIlex may respond, which data is exposed, which model profile is used, what memory can be written and whether the answer is acceptable. The model never receives command execution or arbitrary plugin access.

## Request flow

A direct addressed message or accepted follow-up enters `AssistantChatController`. The controller records compact dialogue state, captures allowed live metadata on the server thread and submits the request to `AssistantRequestCoordinator`. Direct and follow-up requests have priority over proactive work, and queue capacity is bounded.

`AssistantService` then performs:

1. deterministic intent and language classification;
2. context planning;
3. safe live-state capture;
4. query-ranked memory retrieval;
5. reviewed knowledge retrieval or open-ended discovery;
6. adaptive context compilation;
7. one model call with a bounded retry/escalation path;
8. evidence/confidence validation;
9. validated semantic-memory writes;
10. diagnostics and delivery.

## Dialogue and working context

`AssistantConversationManager` keeps the active player↔assistant conversation in memory. It retains a compact multi-turn window for follow-ups rather than only the immediately previous message. Raw ambient chat is a separate short-lived context source and is included only when `WorkingContextPolicy` judges it useful.

Working context is not durable identity. Durable player knowledge is extracted into typed memory instead of storing entire conversations as long-term memory.

## Context planning

`RequiredContextPlanner` selects source families rather than exposing every capability on every request. `ContextCompiler` then allocates the route budget dynamically across:

- the current request;
- active dialogue;
- live Minecraft state;
- durable semantic/episodic memory;
- reviewed HauntedMC knowledge;
- optional recent raw chat.

The configured token limits are ceilings. Simple questions normally consume much less, while difficult grounded questions can use a substantially larger context when evidence exists.

## Live Minecraft state

Live data is captured on the Paper thread and copied into an immutable snapshot before asynchronous model execution. Safe sources can include:

- requester state: gamemode, health, food, saturation, XP/level, air, movement state, ping and effects;
- inventory/equipment summaries: held/offhand item, armor, hotbar/inventory composition;
- world state: world, dimension/environment, biome, rounded location, facing, weather, time, light and difficulty;
- target context: looked-at block/entity type;
- nearby context: player count and non-player entity composition;
- server health: online count, Minecraft version, TPS, MSPT and uptime;
- NPC state: location, movement and current action.

AIlex deliberately excludes IP/network addresses, credentials, hidden staff information, reports/sanctions, plugin configuration, filesystem paths, arbitrary plugin lists and infrastructure secrets.

## HauntedMC integration providers

`AssistantContextProvider` and `AssistantContextProviderRegistry` are the public read-only integration surface for other HauntedMC plugins. A provider returns bounded key/value facts for the requesting player and message. AIlex qualifies and limits those facts before they enter live context.

This is the intended way to expose player-helpful custom state such as a rank, currency balance, CombatTag state, claim state or feature toggle. Providers must never expose secrets or arbitrary internal configuration.

## Reviewed knowledge

`LocalKnowledgeIndex` indexes bundled and external reviewed knowledge articles. Ranking combines BM25-style lexical relevance, aliases, exact command/title signals, multilingual concept expansion, compact dense-hash similarity and redundancy suppression.

Open-ended questions such as “tell me a fun fact” use discovery rather than ordinary query matching. Discovery selects diverse useful chunks from the corpus so weak search terms do not collapse to one accidental fact.

Reviewed knowledge has higher authority than player-learned shared memory when the two conflict. Live state has priority for facts that are current by nature.

## Durable memory

`AssistantMemoryService` keeps the active memory index in memory and serializes durable writes to SQLite/WAL on one dedicated writer. Records include scope, kind, semantic key, value, confidence, salience, provenance, timestamps, supersession and retrieval tags.

### Memory types

- `FACT` — explicit factual player or trusted shared knowledge.
- `PREFERENCE` — explicit stable preference such as response language/style.
- `OPINION` — an explicitly stated subjective view.
- `INTEREST` — an explicitly recurring hobby/topic or gameplay interest.
- `GOAL` — a current project, aim or thing the player is working toward; goals expire unless reconfirmed.
- `RELATIONSHIP` — factual player↔NPC interaction state, not inferred affinity or psychology.
- `EVENT` / `EPISODE` — meaningful time-bound occurrences such as world changes, deaths, advancements or feature-defined events.

### Memory formation and correction

Model-produced memory candidates are structured operations: scope, kind, stable key, value and operation. Candidates are never persisted blindly. The memory service validates source support, sensitivity, scope permissions and kind restrictions.

Player semantic memory is key-addressed across kinds. If the same semantic key changes from a fact to a preference, or its value is explicitly corrected, the old active meaning is superseded instead of coexisting. Explicit “forget” operations remove that semantic key across player semantic kinds. Shared learned memory is fact-only and permission-gated.

Repeated confirmation strengthens confidence/salience slightly instead of creating duplicates. Goals are intentionally temporary; stable facts and preferences remain durable unless corrected or forgotten.

### Memory retrieval

Retrieval combines:

- lexical/query relevance;
- phrase match;
- salience;
- source confidence;
- scope relevance;
- memory-kind relevance;
- type-specific recency decay.

A cheap one-hop associative expansion then uses shared key/value tags from the strongest directly relevant memories to activate related memories. This improves multi-hop recall without requiring a separate graph or vector database. Near-duplicate results are removed before prompt assembly.

## Proactive community behavior

Proactive work always has lower scheduling priority than direct player requests.

For question answering, `ConversationParticipationTracker` keeps a tiny volatile recent-speaker window. It detects alternating speakers, recent direct-address history and contextual reply phrasing. `GeneralQuestionDetector` suppresses proactive answers when that looks like a player-to-player conversation. Explicit broadcast cues such as “weet iemand...?” or “anyone know...?” can still make the question eligible.

This tracker is not durable memory and does not persist transcripts.

## Inference and verification

Routes select fast, grounded or deliberate model profiles. Structured output is required whenever evidence verification or memory extraction needs it. Grounded server/live answers may only cite source IDs actually supplied in the request. Responses with invalid source IDs, insufficient grounding or inadequate confidence are rejected or escalated within the configured model-call/deadline limits.

Static-answer caching fingerprints the relevant system prompt, memory, live snapshot and retrieved evidence so stale contextual answers are not reused after their grounding changes.

## Safety boundary

The model can generate player-facing text and propose memory candidates. It cannot:

- execute commands;
- write arbitrary files or database rows;
- change economy or moderation state;
- mutate the Minecraft world;
- inspect arbitrary plugins/configuration;
- control NPC movement/actions directly;
- bypass memory validation or live-data redaction.

Any future write-capable behavior should be implemented as a separately permissioned deterministic capability, not by broadening the model's existing read-only context path.
