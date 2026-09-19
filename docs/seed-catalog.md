# Seed Catalog for manual Smart Search testing

Seed-only helper (issue #16): loads ~30 enriched Saved Items into a **test** Catalog so you can probe structured list Smart Search (filters, limits, cross-lingual hits, Theme Topic deep links). This is **not** a production migration of old catalogs.

## What you get

- Themes: **Food**, **Fitness**, **AI**, **Travel**
- ~30 Saved Items with `source_type`, `title`, tags, some `recommended_by`, English `search_text`
- ~9 Russian bodies (English `search_text`) for cross-lingual questions
- Instagram / YouTube / article URLs where useful
- Synthetic `user_lib_*` Theme Topic pointers (`telegram` + message ids `91001`…)

## Prerequisites

1. Postgres + pgvector up (`docker compose up db` or equivalent).
2. OpenRouter API key set (`FAVA_OPENROUTER_API_KEY`) so embeddings are indexed on seed (otherwise Smart Search replies that AI isn’t configured).
3. A Telegram chat id to attach the seed Catalog to (real group id, or any unused long for DB-only probing).

## Load the seed

```bash
export FAVA_SEED_ENABLED=true
export FAVA_SEED_CHAT_ID=-1001234567890   # your test Catalog chat id
export FAVA_OPENROUTER_API_KEY=…          # required for embeddings
./gradlew :app:bootRun
```

On startup Fava creates the Catalog (if missing) and inserts the corpus once. If that chat already has any Saved Items, seeding is skipped (idempotent).

Unset or set `FAVA_SEED_ENABLED=false` for normal runs.

## Example probes in Smart Search

Once the bot is in that group and Setup matches (or you only care about DB/API behaviour):

- “Give me 3 recipes”
- “What did Anna recommend?”
- Tag-style: Italian dinner / pilates / packing
- “Instagram” + a since filter if your question states a date
- A Russian question that should still hit an English save via `search_text` (e.g. about embeddings or pilates)

Deep links in answers use `https://t.me/c/<id>/<user_lib_item_id>`; synthetic ids won’t open real Telegram messages unless you seeded a real group and later replace pointers.
