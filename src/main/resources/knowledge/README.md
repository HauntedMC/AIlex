# AIlex reviewed HauntedMC knowledge

This directory is the player-facing, reviewed HauntedMC knowledge corpus used by Haunty. It is intentionally much more detailed than the runtime system prompt: factual server questions are answered by retrieving the relevant chunks from this corpus.

## Source and freshness policy

Use sources in this order when facts conflict:

1. trusted live Paper/Velocity state exposed by AIlex for facts that are inherently live;
2. operator-confirmed current facts;
3. the newest official HauntedMC page, rule, help page, changelog or announcement;
4. older official HauntedMC documentation that has not been contradicted;
5. reviewed third-party observations only for corroboration;
6. historical/legacy material only for historical questions.

A newer specific statement overrides an older statement about the same subject. Historical articles remain searchable so Haunty can answer questions about the server's past, but they must not be used to describe current availability, rules, ranks, prices, schedules or versions.

## Managed resources

Files listed in `index.txt` are bundled and overwritten from the plugin JAR on every AIlex startup. Operator-authored extra knowledge must use a filename that is not listed in the manifest. `entities.tsv` contains reviewed canonical identifiers and is also managed by the manifest.

Each Markdown article uses front matter with `id`, `title`, `aliases`, `category`, `authority`, `updated`, `expires` and `source`. `##` headings become independent retrieval chunks, so long subjects should be split into useful factual sections instead of one giant passage.

Recommended authority values:

- `operator-confirmed`: current fact explicitly verified by HauntedMC operators;
- `official`: current HauntedMC public documentation;
- `reviewed`: fact reconciled from older official material and current evidence;
- `trusted`: useful corroborating source;
- `historical`: superseded or time-bound HauntedMC history.

## What belongs here

Include information a normal player may safely know: commands, gameplay systems, rules, public account/store/help information, public community identifiers, current game-mode state, public update details, and historical server facts where clearly marked.

Never include passwords, API keys, tokens, IPs for private infrastructure, database details, internal staff notes, reports, sanctions, private player data, anti-cheat internals, exploit instructions, or unpublished operational information.

## Volatile facts

Do not hard-code volatile values such as online player count, current queue size, current jackpot, exact store prices, limited-time discounts, temporary event times, current staff activity or exact current server version unless the article is explicitly a short-lived snapshot with an expiry date. Prefer live server state, `/ranks`, `/vote`, `/staff`, the current store, the current event announcement, or support.

## Maintenance

When updating knowledge, review old pages too. Do not simply append a new article that contradicts an old one: either update the old managed file, recategorize the older statement as historical, or remove the obsolete current claim. After changes run the knowledge regression tests and `/ailex ai rebuild-index` (or restart AIlex) so the local hybrid index and embeddings are rebuilt.