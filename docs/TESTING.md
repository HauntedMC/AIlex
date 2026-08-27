# Testing and Quality

AIlex has two kinds of quality checks:

1. **Deterministic CI** proves that routing, memory, retrieval, evidence handling, privacy, tools, social policy and action validation behave predictably without a live model.
2. **Model/provider evaluation** measures answer quality and production behavior separately. A stochastic OpenAI response must never be required for a pull request to pass.

## Commands used by CI

Run the complete local build with:

```bash
./gradlew clean build
```

The two CI gates can also be reproduced directly:

```bash
./gradlew --no-daemon checkstyleMain checkstyleTest
./gradlew --no-daemon test jacocoTestReport jacocoTestCoverageVerification
```

JaCoCo currently enforces the repository regression floor of 55% line coverage and 40% branch coverage.

## What must be deterministic

The following behavior belongs in normal JUnit/AIlexBench coverage and must not call an external model:

- intent, language and model-mode routing;
- semantic-need refinement with deterministic fake embeddings;
- prompt composition and stable epistemic/policy invariants;
- lexical + semantic retrieval fusion and lexical fallback;
- knowledge front-matter parsing, authority, source, update date and expiry;
- context clipping and token ceilings;
- temporal memory queries and truth resolution;
- correction, supersession and explicit forgetting;
- consolidation, lifecycle maturation, interference-aware retention and reconsolidation rules;
- graph/associative memory retrieval and topic views;
- shared-memory synchronization and tombstones;
- typed read-tool availability and execution;
- evidence provenance, line-level grounding and abstention;
- privacy boundaries and cross-player scope isolation;
- proactive conversation suppression and community-goal utility decisions;
- physical action validation and verified action outcomes;
- model-call/tool-call/deadline budgets and request prioritization.

When production exposes a deterministic failure mode, add the failing scenario before or together with the fix.

## AIlexBench

`src/test/java/nl/hauntedmc/ailex/assistant/bench/AIlexBenchTest.java` is the cross-component cognition regression layer.

Focused unit tests answer questions such as “does this parser reject malformed metadata?” AIlexBench should answer questions closer to “given this conversation, memory state and server evidence, what is AIlex allowed to remember, retrieve, say or do?”

The benchmark categories are informed by long-term-agent research but are adapted to Minecraft/server behavior. Important scenario families include:

- accurate memory recall;
- multi-session/long-range continuity;
- temporal reasoning;
- knowledge updates and corrections;
- selective forgetting;
- multi-hop/associative recall;
- abstention when evidence is insufficient;
- tool selection when information is missing;
- using remembered state in a later tool/action decision;
- staying silent in likely player-to-player conversation;
- privacy and capability-boundary violations.

Do not invent retrospective “1.6 scores.” Release comparisons are only meaningful once a scenario and scoring rule existed at the time of measurement or can be replayed reproducibly against the older code.

## Prompt tests

`AssistantPromptComposerTest` protects the prompt architecture from quietly turning back into a large repeated instruction blob.

Tests should verify that:

- stable safety/epistemic rules live in the stable system prefix;
- turn-specific language/length/action instructions stay dynamic;
- Structured Outputs carries the schema rather than the prompt redundantly spelling out every JSON field;
- procedural experience is described as strategy context, not factual authority;
- persona prompts cannot override deterministic policy;
- planner instructions remain compact and tell the model to stop retrieving when evidence is sufficient.

Prompt tests assert important invariants and absence of dangerous duplication; they should not snapshot every word of a prompt.

## Knowledge and retrieval

### Lexical precision

Exact commands, feature names and server terminology need strong lexical behavior. Tests cover command/title/alias matches, phrase boosts, Dutch/English concept expansion, expiry and duplicate suppression.

Adding embeddings must never make an exact `/command` lookup worse.

### Learned semantic recall

`NeuralHybridRetrievalTest` uses a deterministic fake `SemanticEmbeddingProvider`. At least one test should have no useful lexical overlap and succeed because the fake semantic vectors align. Another verifies that an unavailable provider falls back to lexical retrieval without blocking.

Production smoke tests can separately confirm that the configured embedding endpoint is available and that `/ailex ai status` reports semantic retrieval as active.

### Knowledge provenance

`KnowledgeDocumentParserTest` verifies the Markdown front-matter contract. Tests cover stable IDs, aliases, authority, source, update date, expiry and fail-closed handling of malformed/unsupported metadata.

A provenance field that exists only in documentation but is ignored by retrieval is a bug.

## Memory

Memory tests distinguish the **active view** from **historical rows**.

A corrected value must become current from the correction time onward without retroactively rewriting what AIlex believed before that time.

Coverage includes:

- SQLite/WAL persistence and repository reload;
- optional shared MySQL synchronization;
- player/shared/NPC/event scope rules;
- safe candidate acceptance and sensitive/invented candidate rejection;
- stable-key update and supersession;
- explicit forget/tombstone behavior;
- current-vs-historical `MemoryTruthResolver` results;
- disputed close conflicts;
- event and episode storage;
- relationship continuity;
- graph-assisted associative recall;
- lifecycle stage derivation;
- consolidation eligibility;
- interference-based retention/decay;
- verified-use reconsolidation;
- topic-structured context with evidence identity retained.

Retrieving a memory must not increase its factual confidence. Reconsolidation may affect accessibility/salience only after a verified use/outcome path.

## Verified procedural experience

`AssistantExperienceMemoryService` stores lessons about how AIlex behaved, not facts about a player.

Tests must ensure experience is written only from a deterministic/trusted outcome such as a verified correction, grounding failure, successful read-tool path or validated physical-action result. Free-form model self-criticism is never sufficient.

Experience may influence later strategy but cannot be offered as factual evidence for a player-facing server claim.

## Evidence and grounding

Grounded routes use `EvidencePacket`, `AssistantEpistemicPolicy` and line-level `claim_evidence`.

Tests cover:

- unknown evidence ID rejection;
- correct provenance family for server/live/memory routes;
- positive vs negative observations;
- `knowledge.none`, `memory.none` and `live.*.none` causing search/abstention rather than validating an invented positive fact;
- every factual output line being covered by valid evidence IDs;
- partial multi-line grounding rejection;
- ordinary casual chat remaining possible without fake citations;
- bounded retry/escalation when an answer is ungrounded.

## Typed read-agent

The read-agent is tested as a bounded information-acquisition controller.

Important cases:

- sufficient initial evidence skips planning;
- temporal history can request a memory timeline;
- missing HauntedMC evidence can trigger a focused knowledge search;
- only explicitly registered and permitted tools execute;
- malformed and unknown tool calls fail closed;
- equivalent duplicate tool calls are suppressed;
- tool output contains only already-authorized memory/knowledge/frozen-live information;
- planner/tool/model/deadline budgets are respected.

No test should grant arbitrary commands, SQL, filesystem or plugin reflection merely to demonstrate “agent” behavior.

## Social behavior

False-positive intervention is more harmful than AIlex missing an opportunity to speak.

`SocialConversationGraph`, `ProactiveInterventionPolicy` and `ProactiveGoalService` should be tested for:

- a self-contained public question;
- direct address to another player;
- alternating player-to-player dialogue;
- contextual replies inside a recent thread;
- explicit broadcast cues overriding ordinary suppression;
- welcome/help/celebrate/connect/defuse/inform goals;
- `SILENCE` as a successful policy result;
- privacy, error and repetition costs;
- private follow-ups derived only from an explicit stored goal;
- cooldown/age bounds on follow-ups;
- no persistent friendship/psychological inference.

## Controlled actions

The model only proposes an action. Tests for `AssistantActionService` and action-outcome recording must prove that deterministic code rejects proposals unless the player's message explicitly requests the matching allowed action and current NPC/world state is compatible.

Validated outcomes can become events/procedural lessons. The model cannot label its own physical action as successful.

## Shared memory

For a multi-runtime deployment, operational testing should verify:

1. runtime A writes a safe memory;
2. runtime B sees it after the configured synchronization interval;
3. a correction replaces the active value on both runtimes;
4. a tombstone removes the active value on both runtimes;
5. synchronization remains off the Paper tick thread;
6. a burst larger than one change page is fully consumed;
7. an unavailable explicitly configured MySQL backend does not silently create independent authoritative SQLite histories.

## Manual smoke test

After deploying a candidate build, inspect:

```text
/ailex ai status
/ailex ai usage
/ailex memory status
/ailex trace recent
```

Then exercise at least:

- normal conversation and a contextual follow-up;
- current biome/item/live feature state;
- an exact HauntedMC command question;
- a semantic paraphrase of a knowledge item;
- an explicit preference/fact/goal;
- a correction and historical recall of the old value;
- an explicit forget request;
- a question with insufficient evidence that should abstain;
- a two-player conversation AIlex should not interrupt;
- an explicit public question AIlex may answer;
- an allowed physical NPC request and a similar request that should be rejected.

For a shared-memory deployment, repeat correction/forget tests across two Paper runtimes and confirm convergence.
