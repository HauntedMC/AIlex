# Development Notes

This page is for contributors who want a fast, reliable local workflow for AIlex.

## Local Setup

```bash
./gradlew compileJava
```

Useful commands during development:

```bash
./gradlew test
./gradlew checkstyleMain checkstyleTest
./gradlew test jacocoTestReport
./gradlew build
```

## Recommended Workflow

1. Create a branch for one focused change.
2. Implement the change with tests in the same pass.
3. Run local validation (`test` and lint at minimum).
4. Update docs when behavior or operator workflow changes.
5. Open a PR with context, impact, and migration notes (if any).

## Engineering Guidelines

- Keep action and movement responsibilities separated.
- Prefer explicit stop/guard conditions over hidden side effects.
- Keep config keys backward-compatible where possible.
- Isolate external API/network logic from core movement primitives.
- Favor small, testable units over broad command handlers.

## Before You Open a PR

- Build succeeds locally.
- Relevant tests pass.
- New behavior is covered by tests.
- Lint checks pass.
- Operator-visible errors are logged clearly.
