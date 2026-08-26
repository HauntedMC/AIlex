# Development

## Local setup

Requirements are the Java toolchain configured by Gradle and access to the declared Paper/Citizens dependencies.

Run full validation with:

```bash
./gradlew clean build
```

For faster iteration:

```bash
./gradlew test
./gradlew checkstyleMain checkstyleTest
```

## Cognitive architecture rules

Use a **deterministic-first** design. Routing, source permissions, live-state capture, retrieval budgets, memory acceptance, temporal conflict resolution, privacy and final grounding are server-code responsibilities. A model should only make a decision when language/semantic reasoning materially improves the result.

Keep Paper/Bukkit access on the server thread. Copy required live state into an immutable request snapshot before asynchronous model execution.

The model-facing capability path is read-only. New gameplay integrations should expose bounded player-safe facts through `AssistantContextProvider`; never hand the model arbitrary plugin objects, command execution, SQL, filesystem access or mutation APIs.

Durable memory writes must pass `AssistantMemoryService` validation. Do not persist model-proposed memory directly and do not use raw transcript replay as long-term identity.

## Adding retrieval logic

Prefer complementary retrieval signals over one giant prompt. Exact commands/server terminology should retain lexical precision; semantic embeddings should recover paraphrases. Any semantic layer must have a deterministic fallback when the embedding provider is unavailable.

When changing `LocalKnowledgeIndex`, test:

- exact/alias/phrase precision;
- semantic-only paraphrase recall;
- reciprocal-rank/fusion ordering where relevant;
- expiry and authority behavior;
- duplicate/diversity suppression;
- lexical fallback without embeddings;
- retrieval call/cache cost.

Do not use a larger context window as a substitute for better retrieval.

## Adding read-agent capabilities

A read tool is acceptable only when all of these hold:

1. it is player-safe and read-only;
2. deterministic code validates arguments and output;
3. it can return attributable evidence;
4. it solves a real information gap not already covered by initial context;
5. it fits the bounded model-call/deadline budget.

Keep the tool surface explicit. Do not introduce generic reflection, arbitrary HTTP, shell, SQL, plugin dispatch or command tools.

The planner should normally use the cheap/fast model. Information acquisition does not need the most expensive reasoning model when the final grounded answer can do the reasoning.

## Adding server knowledge

Reviewed HauntedMC facts belong in concise Markdown/text knowledge files with useful headings/aliases and player-safe content. Open-ended discovery should have useful positive facts across several topics.

Do not add credentials, private player data, reports, sanctions, internal infrastructure details or speculative facts.

## Adding live integration state

Implement `AssistantContextProvider` and register it with `AIlexPlugin#getAssistantContextProviderRegistry()`. Provider output must be compact, already-loaded, player-facing and non-sensitive. Providers execute on the trusted server path; they must not perform blocking remote/database work during live capture.

## Memory changes

When adding a memory kind, formation rule or retrieval signal, test:

- explicit/source-supported extraction;
- privacy filtering;
- scope/ownership isolation;
- correction and stable-key supersession;
- historical timeline/truth behavior;
- explicit forgetting where applicable;
- persistence/repository synchronization;
- normal-query relevance and associative recall;
- interaction with other semantic kinds;
- lifecycle/decay for temporary concepts.

Temporary current state should have an explicit lifetime rather than silently becoming permanent identity.

Procedural experience is separate from player identity. Only deterministic verified outcomes may create experience lessons; model self-criticism alone is not a trusted write source.

## Grounding changes

Any evidence-bearing answer must remain fail-closed. Evidence IDs must originate in the request, and every emitted grounded line requires an explicit mapping. If a new output format weakens source attribution, add the validation before shipping it.

Prefer abstention or a bounded escalation over guessing a custom/time-sensitive HauntedMC fact.

## Proactive behavior

Proactive participation is conservative and lower priority than direct requests. New triggers need deterministic eligibility and intervention checks before model execution. Test false-positive interruption, decay/expiry of social signals, and explicit broadcast behavior—not only successful triggering.

Do not persist a social graph or infer friendships/personality from normal chat. The current interaction graph is transient pair strength solely for intervention decisions.

## Evaluation discipline

Add deterministic regressions to `AIlexBench` when a change affects core cognitive behavior. Keep model/provider integration tests separate from deterministic CI properties.

Research papers can motivate a technique, but they do not prove that technique works in AIlex. Preserve only components that improve the server workload in tests/production measurements without disproportionate token, latency or operational cost.
