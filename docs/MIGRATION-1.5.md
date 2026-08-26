# Migrating AIlex 1.4.x to 1.5.0

AIlex 1.5 changes the assistant runtime substantially. NPC data remains in `data.yml`; the important migration is assistant memory/config state.

## Before upgrading

1. Stop the Paper server cleanly.
2. Back up the complete `plugins/AIlex/` directory.
3. Keep the existing 1.4 files during the first 1.5 startup, especially:
   - `assistant-memory.yml`
   - `assistant-long-term-memory.yml`
   - `assistant-short-term-memory.yml` if present
4. Replace the plugin JAR with AIlex 1.5.0 and start the server.

## Config migration

AIlex 1.5 introduces `config_version: 2`.

On the first load of an older config, AIlex deliberately changes one privacy-sensitive legacy default:

```yaml
openai:
  chat_context:
    persist_to_disk: false
```

The 1.4 runtime commonly persisted raw short-term chat context. In 1.5 durable state belongs in typed Memory V2, so raw transcript persistence is disabled during the v1→v2 migration.

After the config has version 2, an operator may explicitly set `persist_to_disk: true` again. That v2 opt-in is preserved on later reloads/upgrades.

## Durable memory migration

AIlex 1.5 creates:

```text
plugins/AIlex/assistant-memory.db
```

The database is SQLite using WAL. At startup Memory V2 checks for existing 1.4 memory files and imports supported non-sensitive preferences/shared/player facts once.

After a successful import AIlex creates the migration marker:

```text
plugins/AIlex/.assistant-memory-v2-migrated
```

The original YAML files are **not overwritten or deleted**. This makes rollback and manual verification possible.

### What is not imported

`assistant-short-term-memory.yml` is raw short-lived chat/metadata state, not durable semantic memory. It is not promoted into Memory V2 and is not restored by default. AIlex 1.5 no longer bundles the file and will create/use short-term YAML only when an operator explicitly enables `openai.chat_context.persist_to_disk`.

After verifying the upgrade, old YAML files may be archived outside the plugin directory. Keeping the legacy semantic-memory YAML files is also safe; the migration marker prevents repeated import.

## Verify the upgrade

As an operator, check:

```text
/ailex memory status
/ailex memory recent
/ailex ai status
/ailex ai usage
/ailex trace recent
```

Then test at least these chat paths:

1. Address Haunty with a normal question and verify a response arrives.
2. While Haunty is processing, send another addressed turn. It must be queued/replaced with visible feedback rather than disappear.
3. Continue a conversation with a short follow-up such as `waarom?` without repeating the NPC name.
4. Ask a vanilla Minecraft question and confirm AIlex can answer from general Minecraft knowledge without requiring a HauntedMC article.
5. Ask a current-state question such as what item you are holding and confirm the reply uses live state.
6. Ask about a remembered preference or a meaningful recent event after one has been recorded.

## Models and cost

The shipped adaptive profiles are:

- fast: `gpt-5.6-luna`, low reasoning;
- grounded: `gpt-5.6-terra`, medium reasoning;
- deliberate: `gpt-5.6-sol`, high reasoning.

The 1.5 runtime also uses route-specific input budgets, short output caps, conditional structured output and provider prompt-cache accounting. Use `/ailex ai usage` after real traffic to verify input/cached/cache-write/output token behavior before changing budgets.

## Rollback

If you must return to 1.4.x:

1. Stop the server.
2. Restore the 1.4 JAR and the backed-up 1.4 `config.yml`.
3. Restore the old YAML assistant memory files if they were moved after verification.
4. `assistant-memory.db` and `.assistant-memory-v2-migrated` are 1.5-specific and can remain unused or be moved aside.

Do not try to convert the SQLite database back into 1.4 YAML by hand.

## Privacy notes

- OpenAI `store_responses` remains `false` by default.
- Raw chat transcript disk persistence is disabled by default in config v2.
- Durable memory rejects configured sensitive categories such as credentials, contact details, precise coordinates, reports and sanctions.
- Reviewed server knowledge still belongs in `knowledge/*.md`, not in player memory.
