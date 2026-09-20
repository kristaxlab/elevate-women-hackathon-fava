## Problem Statement

Save and search already work inside Fava, but only through Telegram (Inbox filing and Smart Search). Developers iterating on enrichment, embeddings, filters, and retrieval quality have no direct way to drive those paths: they must stage messages in a group, wait on theme picks and Telegram side effects, and read answers shaped for chat rather than raw ranked items and vectors.

That slows harness work now, and later the same gaps hurt debugging and any eventual split into services that need to call catalog write/search without going through Telegram.

## Solution

Expose a small, unauthenticated **developer HTTP API** that wraps existing catalog and search behavior (not the Telegram filing UI):

- Create an already-enriched Saved Item with URL dedupe and embedding generation, returning the item plus nested embedding info.
- List all Saved Items for a catalog, each with nested embedding info (or null).
- Turn a natural-language catalog question into a `StructuredQuery` without persisting it.
- Run retrieval from a `StructuredQuery` and return ranked Saved Items with distances.

Errors use RFC 7807 problem+json with distinct problem type URIs; server-side logging only—no auth or product API ceremony in this slice.

## User Stories

1. As a developer, I want to create a Saved Item over HTTP with an already-enriched payload, so that I can seed catalogs without Telegram Inbox filing.
2. As a developer, I want create to generate and persist an embedding for the item, so that the item is immediately searchable.
3. As a developer, I want the create response to include the same Saved Item plus nested embedding info (model id, dimensions, vector), so that I can verify embed ran and inspect the vector.
4. As a developer, I want `chatId` on the create body (as part of the Saved Item shape), so that catalog identity is explicit and matches the domain model.
5. As a developer, I want create to reject a non-null `id` with 400, so that I cannot accidentally imply client-assigned primary keys.
6. As a developer, I want create to default `sourceMessageId` to 0 and leave user-lib fields empty when omitted, so that non-Telegram saves do not require fake Telegram identifiers.
7. As a developer, I want create to validate that `themeName` exists on the catalog, so that orphan theme names do not pollute filters and browse.
8. As a developer, I want a missing catalog on create to return 404 with a catalog-not-found problem type, so that I can tell setup was never run for that `chatId`.
9. As a developer, I want an unknown `themeName` on an existing catalog to return 404 with a theme-not-found problem type, so that I can distinguish theme mistakes from missing catalogs.
10. As a developer, I want URL dedupe on create: if the URL already exists in that catalog, return 200 with the existing item and its stored embedding (no re-embed), so that repeats are idempotent and cheap.
11. As a developer, I want create without a URL to always insert (no dedupe), so that note-like items behave like the filing path.
12. As a developer, I want embed/provider failure on create to return 502 and persist nothing, so that I never get searchable-looking rows without vectors from this API.
13. As a developer, I want create success to return 201, so that new inserts are distinguishable from idempotent duplicates (200).
14. As a developer, I want to list all Saved Items for a `chatId` via GET with a query param, so that I can inspect catalog contents after seeding.
15. As a developer, I want each list element to use the same `{ item, embedding }` envelope as create, so that clients reuse one representation.
16. As a developer, I want `embedding: null` when a row has no vector, so that partial sync or legacy gaps are visible.
17. As a developer, I want GET on a missing catalog to return 404, so that list failures are explicit.
18. As a developer, I want GET on an existing empty catalog to return 200 with `[]`, so that a new catalog is not confused with “not found.”
19. As a developer, I want to POST natural language as `{"query":"..."}` to obtain a `StructuredQuery`, so that I can inspect and edit distilled query and filters before search.
20. As a developer, I want structured-query conversion to not persist anything, so that parse experiments leave no residue.
21. As a developer, I want structured-query conversion to return 502 (problem+json) when the chat model fails, so that harness results are not silently degraded by the in-process Soft Search fallback.
22. As a developer, I want to POST a `StructuredQuery` body to search items with `chatId` (and optional `maxDistance`) as query params, so that I can pipe parse output into retrieval and tune the distance cutoff.
23. As a developer, I want `maxDistance` to default to the same value production Smart Search uses (0.45), so that harness rankings are comparable to Telegram search.
24. As a developer, I want search results as `[{ item, distance }, ...]`, so that I can judge retrieval quality without Telegram citation formatting.
25. As a developer, I want search to omit embedding payloads on hits, so that responses stay small while ranking.
26. As a developer, I want missing catalog on search to return 404 with a catalog-not-found problem type, so that identity errors are clear.
27. As a developer, I want zero hits (hard filter miss or all distances above cutoff) to return 404 with a no-search-hits problem type, so that empty retrieval is loud during harness work.
28. As a developer, I want blank/invalid structured query or missing `chatId` on search to return 400, so that client mistakes are separated from empty corpora.
29. As a developer, I want provider/search-stack unavailability (no key, embed fail, chat fail) to return 502, so that one status covers “cannot talk to the model stack.”
30. As a developer, I want all error bodies as RFC 7807 problem+json with distinct `type` URIs per failure class, so that scripts can branch without scraping free text.
31. As a developer, I want errors logged server-side with enough context to debug, so that I do not need auth or admin UI for this slice.
32. As a developer, I want these endpoints unauthenticated for now, so that local and CI harness use stays low-friction.
33. As a future maintainer, I want controllers in the catalog and search modules (with web starter deps) and deep use-cases behind them, so that a later microservice split can lift those modules without inventing a parallel API layer.
34. As a developer, I want create and search HTTP paths to avoid Telegram copy-to-thread, theme-pick callbacks, and Inbox enrichment, so that harness traffic does not depend on Bot API side effects.
35. As a developer, I want unexpected failures to return 500 problem+json, so that crashes are still machine-readable.

## Implementation Decisions

### Modules

- **Catalog HTTP adapter** (catalog module): `POST /api/catalog/items`, `GET /api/catalog/items`. Maps HTTP to the write/list use-cases; emits problem+json; logs.
- **Catalog item write use-case** (deep module): validate catalog and theme; URL dedupe; embed; transactional persist of item + embedding; return item + embedding info. Defaults for non-Telegram fields. Fail closed if embed cannot complete.
- **Embedding read seam** (extend embedding store): read embedding and model metadata by saved-item id; support list/batch so GET can nest vectors (or null).
- **Search HTTP adapter** (search module): `POST /api/search/structured-queries`, `POST /api/search/items`. Maps HTTP to parse/retrieval use-cases; problem+json; logs.
- **Structured search use-case** (deep module): `(chatId, StructuredQuery, maxDistance) → ranked hits` or typed outcomes (missing catalog / no hits). Reuses filter → embed query → similarity → hydrate. Does not use the NL `CatalogSearchPort.answer` path (no citation intro / Telegram formatting).
- **Structured-query parse exposure**: HTTP invokes the existing parser; HTTP semantics are stricter on model failure (502, no soft fallback).

### API contracts

| Method | Path | Notes |
|---|---|---|
| POST | `/api/catalog/items` | Body = Saved Item shape; `chatId` in body; `id` must be absent/null |
| GET | `/api/catalog/items?chatId=` | List all items in catalog |
| POST | `/api/search/structured-queries` | Body `{"query":"<nl>"}` → `StructuredQuery` |
| POST | `/api/search/items?chatId=&maxDistance=` | Body = `StructuredQuery`; `maxDistance` optional, default 0.45 |

**Create/list item envelope:** `{"item": <SavedItem>, "embedding": {"modelId": string, "dimensions": number, "vector": number[]} | null}`

**Search hit envelope:** `{"item": <SavedItem>, "distance": number}`

### Status and problem types

| Case | Status | Problem `type` (distinct URI per class) |
|---|---|---|
| Create OK | 201 | — |
| Create duplicate URL | 200 | — |
| Create `id` non-null / blank required fields / bad JSON / missing search chatId / blank NL or structured query | 400 | validation |
| Catalog missing | 404 | catalog-not-found |
| Theme unknown (catalog exists) | 404 | theme-not-found |
| Search zero hits | 404 | no-search-hits |
| GET empty catalog (exists) | 200 `[]` | — |
| Embed/provider/chat/search-stack failure or not configured | 502 | provider / upstream |
| Unexpected | 500 | internal |

### Technical clarifications

- No auth in this slice.
- Add web starter dependency to catalog and search modules so controllers can live there; ensure component scan picks them up from the runnable app.
- Create must not use a no-op indexer when the provider is missing: treat as 502 and do not persist.
- Prefer embed externally, then persist item + embedding in one DB transaction so embed failure leaves no row.
- Duplicate path returns existing embedding as stored (no re-index).
- List uses “all items in catalog” (empty filters) and nests embeddings via the new read seam.
- Do not add update/delete Saved Item APIs, Telegram filing orchestration, or product auth in this PRD.

### Schema changes

- No new tables expected. May need store methods to read embeddings (and model metadata) by saved-item id / catalog batch; domain `SavedItem` remains free of nested embedding fields—nesting is an HTTP response concern.

## Testing Decisions

Good tests assert **external behavior** through module interfaces (and HTTP contracts for adapters): status codes, problem `type`s, persistence outcomes, ranking/dedupe semantics. They do not lock onto private helpers, SQL text, or controller method names.

**In scope to test:**

- Catalog item write use-case (create, dedupe/idempotency, theme/catalog not found, embed failure leaves no row, defaults for Telegram-shaped fields).
- Embedding store read extension (round-trip vector + metadata; missing row → null for API mapping).
- Structured search use-case (filters, limit, maxDistance, hydrate + distance, missing catalog, zero hits).
- HTTP adapters (MockMvc or equivalent): status codes and problem+json `type` URIs for the matrix above; happy-path envelopes for create/list/parse/search.
- Structured-queries adapter with a fake chat model: success vs 502 on failure (no soft fallback).

**Prior art:** JDBC/Testcontainers tests for catalog and embedding stores; `CatalogSearchServiceTest` and `StructuredQueryParserTest` for search behavior; almost no existing web-layer tests—adapter tests are new but should stay thin.

## Out of Scope

- Authentication, authorization, rate limiting, API keys, or network exposure hardening beyond “dev harness.”
- Full Inbox filing pipeline (enrichment, theme classification, theme-pick callbacks, Telegram copy-to-thread).
- Update/delete Saved Items; re-embed-only endpoint; pagination/cursors on list or search.
- Returning embeddings on search hits.
- Soft-fallback structured parse on the HTTP parse endpoint.
- Replacing or changing Telegram Smart Search / Inbox UX.
- Splitting the monolith into real microservices (modules should remain extractable later).
- OpenAPI/Swagger publication, versioning scheme, or public product API promises.
- Changing embedding model sync / startup backfill behavior except as needed to read vectors for responses.

## Further Notes

- Audience: developers now; later possible reuse for debugging and inter-service calls if the app is split—contracts should stay boring and domain-aligned, not Telegram-shaped.
- Empty search → 404 is intentional for harness loudness; list empty catalog stays 200 `[]`.
- Production Smart Search may continue soft-falling back on parse failure inside Telegram; only the HTTP structured-queries endpoint is fail-closed.
- Filename: `issues/prd-dev-http-api.md` (does not replace the product `issues/prd.md`).
