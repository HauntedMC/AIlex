# Contributing

Thanks for taking the time to contribute.

## Before You Start

- Use the Java toolchain declared by the Gradle build.
- Make sure you can run a local compile and test pass.
- If your change affects runtime NPC, live-context or proactive behavior, test it on a Paper server.

## Setup

```bash
git clone <repo-url>
cd AIlex
./gradlew compileJava
```

## Contribution Workflow

1. Create a branch from `main`.
2. Keep the change focused on one clear problem.
3. Add or update tests with the code change.
4. Run local checks before opening a PR.
5. Update docs when behavior or configuration expectations change.

## Local Validation

Minimum checks:

```bash
./gradlew compileJava
./gradlew test
```

Recommended before merge:

```bash
./gradlew checkstyleMain checkstyleTest
./gradlew check
```

## Pull Request Expectations

- Use a clear title and summary.
- Explain what changed and why.
- Call out configuration or operational impact.
- Link related issues where relevant.
- Keep commits readable and review-friendly.

## Coding Principles

- Prefer clear, explicit code over shortcuts.
- Keep movement and action boundaries testable.
- Handle malformed input safely.
- Avoid blocking server-critical paths.
- Keep the LLM path read-only; expose custom server state through bounded trusted context providers.
- Treat raw chat as working context, not durable player identity.
- Validate semantic memory writes and player-visible evidence deterministically.
- Log failures in a way operators can act on without leaking sensitive player content.

## Security

Do not report vulnerabilities in public issues.
Follow [SECURITY.md](SECURITY.md) for private reporting.
