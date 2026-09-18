## Problem Statement

People save useful Instagram links, Telegram posts, and similar content in a messy pile of chats, bookmarks, and screenshots. When they later need something—“that DIY device idea” or “a dinner recipe I saved”—they cannot find it quickly, and search across those sources is not grounded in *their* library. Existing tools are either general note apps (extra friction, leave Telegram) or platform-native saves that do not support topic filing and natural-language questions over a personal archive.

For a hackathon prototype named **Fava**, the immediate need is: capture Instagram links and Telegram posts from inside Telegram, organize them under personal topics, and answer questions using only what was saved—without building a separate mobile/web client yet.

## Solution

Fava is a Telegram bot that turns a **forum-enabled supergroup** into a **personal catalog** of saved content.

A user starts (or reuses) a group with Fava’s help, enables Topics, adds the bot as admin, and runs setup to define topics (e.g. AI, Fitness, Preparing for marathon). Fava creates an **Inbox** topic, a **Smart Search** topic, and one forum topic per theme. The user forwards Instagram links or Telegram posts into Inbox; Fava parses what Telegram already provides, saves the item, classifies it into exactly one topic (asking the user when unsure), and files a copy into that topic folder with an Inbox confirmation. In Smart Search, the user asks free-text questions; Fava answers with AI using **only** that group’s catalog and cites matching saves with URLs.

The product is **personal favorites save & search**. Inviting others is ordinary Telegram behavior and needs no special Fava join/member flows. Strangers can each create their own catalog the same way. Stack: latest Java, Spring AI, Docker; OpenRouter (OpenAI-compatible, replaceable); Postgres + pgvector; Gradle multi-module monolith with clear package/module seams for later extraction.

## User Stories

1. As a Telegram user, I want to open a DM with Fava and understand what it does in one short message, so that I know how to create my personal catalog.
2. As a Telegram user, I want a **Create my catalog** action that deep-links me into adding Fava to a new or existing group with the right admin rights, so that I do not have to guess Bot API permissions.
3. As a Telegram user, I want an **I already have a group** path, so that I can attach Fava to a group I already created.
4. As a group owner, I want Fava to detect when it becomes an admin and nudge me to enable Topics and run `/setup`, so that I am not stuck after adding the bot.
5. As a group admin, I want clear instructions when the group is not forum-enabled yet, so that I know exactly which Telegram settings to flip before setup can proceed.
6. As a group admin, I want to run `/setup` and send a comma- or newline-separated list of topic names, so that I can define my filing taxonomy in one step.
7. As a group admin, I want Fava to create Inbox, Smart Search, and one forum topic per theme after I confirm topics, so that the group is ready to use immediately.
8. As a group admin, I want `/setup` to refuse if the catalog is already configured, so that an accidental re-run does not recreate or scramble topics in v1.
9. As a group member (including the owner), I want to forward an Instagram link into Inbox and have it saved, so that it enters my personal library without leaving Telegram.
10. As a group member, I want to forward a Telegram post (with or without a URL) into Inbox and have its text/caption stored, so that channel/group posts are first-class saves.
11. As a group member, I want non-qualifying Inbox noise (e.g. plain notes without forward/URL, in v1) to get a brief rejection, so that I understand what Fava accepts.
12. As a group member, I want Fava to extract URL + caption/link-preview/forwarded text from Telegram only, so that Instagram saving works without fragile external scraping in v1.
13. As a group member, I want each new save classified into exactly one of my configured topics, so that the archive stays browsable by theme.
14. As a group member, I want Fava to ask me with inline topic buttons when classification confidence is low, so that misfiles are rare without forcing a menu every time.
15. As a group member, I want Fava to copy/forward the item into the chosen topic thread after classification, so that I can browse saves by opening that topic.
16. As a group member, I want an Inbox reply like “Filed → Fitness”, so that I get visible confirmation without hunting for the item.
17. As a group member, I want forwarding the same URL twice in the same catalog to yield “already saved → Topic” and no duplicate file, so that search and folders stay clean.
18. As a group member, I want to ask a natural-language question in Smart Search, so that I can retrieve ideas from my library without remembering exact titles.
19. As a group member, I want Smart Search answers grounded only in this group’s saved items, so that the bot does not invent recipes or DIY ideas from the open web.
20. As a group member, I want answers to include a short citation list with titles/snippets and original URLs, so that I can open the underlying save.
21. As a group member, I want Smart Search to behave safely when the catalog is empty or has no relevant hits, so that I get an honest “nothing found” instead of hallucinations.
22. As a group member, I want messages in Smart Search before setup to prompt me (or point admins) to `/setup`, so that the bot does not appear broken.
23. As a group admin, I want only admins to run `/setup`, so that random members cannot redefine the catalog.
24. As a group member, I want to use Inbox and Smart Search without DMing Fava first, so that invitees or co-users of my group have minimal friction (no special Fava invite flow).
25. As a Telegram user, I want to own more than one catalog group if I choose, so that personal vs project libraries can stay separate without a global “active catalog” concept.
26. As a demo presenter, I want the happy path (setup → forward → file → search) to work live with real AI, so that the hackathon pitch is credible.
27. As a developer, I want the app runnable via Docker Compose with Postgres/pgvector, so that any teammate or server can boot the stack quickly.
28. As a developer, I want long polling by default and a webhook profile for deployed HTTPS hosts, so that laptop demos and server deploys both work.
29. As a developer, I want LLM/embedding calls to go through an OpenAI-compatible gateway (OpenRouter) configured by env, so that models and vendors stay replaceable.
30. As a developer, I want domain logic split across Gradle modules (`telegram`, `ingest`, `catalog`, `classify`, `search`, `app`), so that future microservices extraction stays plausible without splitting deployables now.
31. As a future user, I want the design to allow screenshots, YouTube links, and media thumbnails later, so that v1 choices do not paint us into a corner—without implementing those in this PRD.
32. As a product owner, I want English UI strings structured for later i18n, so that we do not ship a second language in v1 but can add one without rewriting flows.
33. As a catalog owner, I want topic names like project titles (“Preparing for marathon”) as well as themes (“AI”), so that filing matches how I think, not only generic tags.
34. As a group member, I want forwarded Telegram posts without URLs to still be searchable by their text, so that screenshot-alternate captures (text posts) remain useful.
35. As a developer, I want configuration and secrets (bot token, OpenRouter key, DB URL) supplied via environment variables, so that Docker deploys stay simple and safe.

## Implementation Decisions

### Architecture
- Single deployable Spring Boot application; **Gradle multi-module** for boundaries; not separate networked microservices in v1.
- Modules:
  - **`telegram`** — Bot API adapter: updates (long polling / webhook profile), send/reply, forum topic create, copy/forward into `message_thread_id`, inline keyboards. Emits/handles application commands; does not own catalog or AI rules.
  - **`ingest`** — Normalize Inbox updates into a ContentItem draft (chat/catalog id, URLs, caption/forward text, message ids, sender). Apply URL dedupe check against catalog before classify/file.
  - **`catalog`** — Persistence for catalog setup state, topics (name ↔ Telegram `message_thread_id`), saved items, and vector/document linkage. Postgres + pgvector behind a small interface (setup, save, find-by-URL, retrieve for RAG).
  - **`classify`** — Map ContentItem + topic list → single topic or `needs_user_pick`. OpenRouter/Spring AI hidden behind this interface.
  - **`search`** — RAG: embed query, retrieve from catalog vectors, generate answer + citations. No Telegram dependency.
  - **`app`** — Bootstrapping, configuration, profile wiring, Docker entrypoint.
- Chat type: **forum-enabled supergroup** only (not broadcast channels). Bot needs admin rights including **manage topics** and ability to post/copy into topics.
- Tenancy: **one Telegram group = one catalog**. No Fava-side invite/member/join processing. Personal-use product; multi-member access is whatever Telegram allows.
- Onboarding: DM `/start` with Create / I already have a group → `startgroup` deep link requesting admin permissions → in-group nudge → `/setup`.

### Domain rules (v1)
- Topics configured **once** at setup; no reconfigure UI in v1.
- System topics: **Inbox**, **Smart Search**, plus user themes.
- Ingest accepts: messages with **http(s) URLs** and/or **forwarded Telegram posts**.
- Store: URL (if any), text Telegram provided, topic assignment, timestamps, Telegram message references, catalog (chat) id. **No Instagram API/oEmbed scrape** in v1. Media download/thumbnails deferred.
- Classification: **exactly one** topic; low confidence → inline buttons listing configured themes (not Inbox/Smart Search).
- After successful file: **reply on the Inbox message** with filed topic; leave Inbox message in place.
- Dedupe: same **URL** within the same catalog → skip re-file, report existing topic.
- Smart Search: free text in that topic → answer + bullet citations (title/snippet + URL). Grounding = catalog only.
- Permissions: **admins** `/setup`; **any member** who can post may use Inbox and Smart Search.
- UI language: English strings; keep copy centralized for later i18n (do not ship second locale).

### AI & data
- Spring AI with **OpenAI-compatible** client pointed at **OpenRouter** (`base-url`, api key, model ids via env).
- Chat model for classify + answer generation; embedding model for RAG; dimensions must match pgvector config.
- Vector store: **pgvector** in the same Postgres instance as relational catalog data.

### Telegram delivery
- Default: **long polling**.
- Optional Spring profile: **webhook** for HTTPS deployments.

### Explicit non-goals in implementation
- No channel-based “folders”; no bot-created groups (impossible via Bot API—guide the user instead).
- No multi-label filing; no per-user filter inside a shared group; no DM-global search across all of a user’s catalogs in v1.

## Testing Decisions

### What makes a good test
- Test **external behavior** through each module’s public interface (inputs/outputs, invariants, error cases).
- Do **not** assert on prompts, private helpers, or Spring wiring details unless a thin smoke test is needed.
- Prefer fakes at seams: fake `ChatModel`/`EmbeddingModel`, in-memory or Testcontainers catalog, fake Telegram sender—avoid live OpenRouter/Telegram in CI.

### Modules under test (v1)
- **`ingest`** — URL/forward extraction; rejection of unsupported shapes; interaction with dedupe decisions.
- **`catalog`** — setup persistence; save/find-by-URL; topic thread id mapping; empty-catalog behavior. Prefer Testcontainers Postgres/pgvector where embeddings are involved.
- **`classify`** — maps model output to single topic vs needs-user-pick; does not call real LLM (fake model stub).
- **`search`** — retrieval + answer/citation shaping with fake LLM and controllable vector contents (Testcontainers or stubbed vector store).
- **`telegram`** — light mapping tests only if time (update → command); no live Bot API requirement in CI.
- **`app`** — optional context-load smoke; not a focus.

### Prior art
- Greenfield repository: no existing test suite. Establish module-local unit/integration tests as the pattern going forward.

## Out of Scope

- Screenshots as a first-class ingest type (and OCR).
- YouTube (and other platforms) specialized parsers beyond generic URL + Telegram text.
- Downloading/storing media files; thumbnail generation (stretch only if time remains after the happy path).
- Instagram/Meta API or HTML scraping for captions.
- Reconfiguring, renaming, or deleting topics after initial setup.
- Multi-label topic assignment; manual “move to topic” beyond low-confidence buttons.
- Team/workspace product features (invite workflows, roles beyond Telegram admin, per-user libraries inside one group).
- DM search across all catalogs a user owns; account system beyond Telegram identities implied by chat membership.
- Separate microservice deployments, Kubernetes, or multi-region ops.
- Native iOS/Android/web clients.
- Billing, usage quotas, or multi-tenant SaaS admin.
- Channel (broadcast) support; non-forum groups as a supported end state.
- Conversation memory / multi-turn agent tools beyond single-turn RAG Q&A in Smart Search (unless trivially needed for button callbacks).

## Further Notes

- Product name in copy: **Fava**.
- Hackathon demo script: DM create/add bot → enable Topics → `/setup` with 2–3 topics → forward 2–3 Instagram/Telegram items into Inbox → show filing + confirmation → ask one Smart Search question that cites a save.
- Preferred stretch if time remains after search works: thumbnail for items that already include Telegram photo media—without blocking v1.
- Legal/ToS note for later iterations: external Instagram fetch is restricted; v1’s Telegram-only capture is intentional.
- After this PRD is accepted, natural follow-ons are domain `CONTEXT.md` / ADRs and slicing into implementation issues—not part of this document’s delivery.
