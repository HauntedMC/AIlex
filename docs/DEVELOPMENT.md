# Development

## Local setup

Requirements are the Java toolchain configured by the Gradle build and access to the declared Paper/Citizens dependencies.

Run the full local validation with:

```bash
./gradlew clean build
```

For faster iteration:

```bash
./gradlew test
./gradlew checkstyleMain checkstyleTest
```

## Architecture rules

Keep Paper/Bukkit access on the server thread. Copy any required live state into immutable request context before asynchronous model work begins.

The LLM path must remain read-only. New gameplay integrations should expose bounded player-safe facts through `AssistantContextProvider`, not hand the model arbitrary plugin objects or mutation APIs.

Durable memory writes must go through `AssistantMemoryService` validation. Do not persist raw model-proposed memory directly, and do not turn raw chat transcripts into durable identity.

Prefer deterministic routing, context selection, validation and safety checks over asking the model to decide its own permissions.

## Adding server knowledge

Reviewed HauntedMC facts belong in concise Markdown/text knowledge files. Add aliases and clear headings so retrieval can find them. Open-ended discovery should have several useful positive facts available across different topics.

Do not add credentials, private player data, reports, sanctions, internal infrastructure details or speculative facts.

## Adding live integration state

Implement `AssistantContextProvider` and register it with `AIlexPlugin#getAssistantContextProviderRegistry()`. Keep provider output compact and player-facing. Providers execute as trusted server code, so failures must be safe and must not broaden AIlex's security boundary.

## Memory changes

When adding a semantic memory type or retrieval signal, add tests for:

- extraction/source validation;
- correction/supersession;
- explicit forgetting where applicable;
- persistence;
- privacy filtering;
- retrieval ranking and redundancy;
- interaction with other semantic kinds.

Temporary/current-state concepts should have an explicit lifecycle rather than becoming permanent player identity.

## Proactive behavior

Proactive participation should be conservative. Any new trigger must remain lower priority than direct requests and should have deterministic eligibility checks before model execution. Add tests for false-positive interruption, not only successful triggering.
