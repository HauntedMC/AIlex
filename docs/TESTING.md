# Testing and Quality

Run the complete build before merging:

```bash
./gradlew clean build
```

This includes compilation, unit tests, Checkstyle and coverage reporting configured by the project.

## Assistant routing and context

Tests should verify:

- intent classification and follow-up routing;
- live-state questions select the required source families;
- the current request is never dropped when context is clipped;
- multi-turn dialogue survives prompt compilation;
- live state and memory receive useful space within large grounded budgets;
- excessive history/evidence still stays within the configured route ceiling.

Important manual prompts include `waar ben ik?`, `welk bioom is dit?`, `wat houd ik vast?`, `waar kijk ik naar?` and a server-health question.

## Knowledge retrieval

Test exact commands, Dutch/English concept aliases, redundancy suppression and broad discovery. A prompt such as `vertel een leuk feitje over HauntedMC` should retrieve multiple useful candidate facts rather than collapse to one negative/unrelated fact or return an empty corpus when reviewed knowledge exists.

## Durable memory

Memory tests must cover:

- SQLite persistence;
- sensitive/invented candidate rejection;
- player vs trusted shared scope;
- shared memory accepting facts only;
- stable-key correction and supersession;
- cross-kind semantic replacement;
- explicit forgetting;
- preferences, opinions, interests and temporary goals;
- relationship/event scoping;
- reinforcement on repeated confirmation;
- associative retrieval and duplicate suppression.

Memory tests should assert what remains **active**, not only that a historical row was once written.

## Proactive chat

False positives matter more than raw response rate. Test at least:

1. a self-contained public question with no active conversation;
2. a question directly naming/tagging another player;
3. two players alternating messages followed by a contextual `?` reply;
4. recent direct-address history followed by a question without the name;
5. an explicit broadcast question (`weet iemand...?` / `anyone know...?`) during an active conversation;
6. shared cooldown behavior;
7. direct-request capacity remaining available while proactive work exists.

AIlex should prefer silence when it cannot confidently distinguish a private player conversation from a public question.

## Live integration providers

Provider tests should ensure invalid keys/oversized values are rejected or bounded, provider failures do not fail the whole assistant request, and output is qualified so two integrations cannot silently overwrite each other's facts.

Never test by exposing secrets to the model and expecting the prompt to remove them later; providers themselves must only return player-safe data.

## Reliability and verification

Cover:

- invalid structured output retry limits;
- grounded→deliberate escalation boundaries;
- circuit-breaker behavior;
- queue replacement/supersession;
- evidence-ID validation;
- static cache fingerprints changing with memory/live/evidence;
- deadline fallback behavior.

## Manual production smoke test

After deploying to a test server, verify:

```text
/ailex ai status
/ailex ai usage
/ailex memory status
/ailex trace recent
```

Then run a normal conversation, a multi-turn follow-up, live biome/item queries, reviewed server knowledge, open-ended discovery, a remembered preference, a correction, an explicit forget, and a two-player conversation that AIlex must not interrupt.
