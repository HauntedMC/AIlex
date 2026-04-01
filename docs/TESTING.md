# Testing and Quality

Testing in this project is focused on preventing regressions in movement logic, action behavior, and command/runtime integration points.

## Test Structure

Tests live under `src/test/java` and mirror production package boundaries:

- movement/pathfinding tests for deterministic algorithms;
- action/config/npc tests for runtime behavior and contracts;
- listener/command/util tests for integration-facing logic and guard rails.

## Local Commands

Run tests:

```bash
./gradlew test
```

Run full quality checks:

```bash
./gradlew check
```

Run lint checks:

```bash
./gradlew checkstyleMain checkstyleTest
```

Generate local coverage report:

```bash
./gradlew test jacocoTestReport
```

## What to Test

When changing behavior, add or update tests near the affected boundary:

- movement changes: directional math, clipping, and steering outputs;
- action changes: stop conditions, world/entity guards, and completion behavior;
- command changes: parse/validation logic and operator-visible outcomes;
- config/data changes: load/save semantics and invalid-input handling.

## Test Quality Bar

Use these rules when adding or reviewing tests:

- prefer behavior assertions over "does not throw" assertions;
- validate both happy path and failure/edge path;
- assert observable outcomes (state changes, outputs, interactions);
- avoid overly broad mocks when local deterministic tests are possible.

## Coverage Reports

After `jacocoTestReport`:

- HTML report: `build/reports/jacoco/test/html/index.html`
- XML report: `build/reports/jacoco/test/jacocoTestReport.xml`

## CI

CI validates lint checks, tests, and coverage report generation on pull requests and `main` branch updates.
