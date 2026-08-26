# Testing and Quality

AIlex tests deterministic logic at component boundaries so reliability failures can be reproduced without a live Minecraft server or real model call.

## Local commands

```bash
./gradlew test
./gradlew checkstyleMain checkstyleTest
./gradlew test jacocoTestReport
./gradlew check
```

Coverage reports are written to:

- `build/reports/jacoco/test/html/index.html`
- `build/reports/jacoco/test/jacocoTestReport.xml`

## Test areas

Tests under `src/test/java` cover:

- movement/pathfinding algorithms;
- NPC lifecycle, actions, config and persistence;
- chat mention/routing behavior;
- active player↔NPC dialogue state;
- request admission, queueing, supersession and proactive priority;
- route-specific context/token budgets;
- selective live-context planning;
- local hybrid retrieval;
- Memory V2 validation, supersession, SQLite persistence and legacy YAML migration;
- selective episodic/event memory;
- OpenAI request construction, response parsing and provider usage/cache accounting;
- command/operator diagnostics.

## Reliability regressions

`HauntyIncidentRegressionTest` encodes the production failure that motivated Runtime V2. It verifies that an unresolved addressed turn keeps `haunty?` as a continuation and that newer turns sent while a request is active are queued/superseded instead of silently disappearing.

`AssistantRequestCoordinatorLoadTest` applies deterministic pressure without timing assumptions. With the shipped admission shape it verifies that 100 distinct direct submissions cannot exceed four active + eight queued requests, rejected work cannot grow the queue, proactive traffic gets no direct queue capacity, and accepted work drains completely.

These tests should remain even if the implementation is reorganized; they describe required user-visible behavior rather than a specific internal class layout.

## Assistant quality bar

When changing assistant behavior, test both the selected route and what is *not* selected. Examples:

- casual chat should not capture world/server state;
- nearby-player questions should not automatically capture global server population;
- event recall should use episodic memory rather than unrelated live context;
- vanilla gameplay answers may be accepted without HauntedMC evidence;
- custom/time-sensitive HauntedMC facts must not invent evidence IDs;
- an active follow-up should preserve unresolved dialogue intent;
- queue pressure must produce explicit accepted/queued/rejected outcomes.

For memory, assert provenance/scope/expiry/supersession rather than only checking a rendered summary string.

For OpenAI integration, mock HTTP and assert exact request fields and provider-reported token/cache parsing. Tests must never depend on a real API key.

## CI

Pull requests run separate lint and test/coverage workflows. A release candidate is not considered ready until both workflows pass on the current PR head.
