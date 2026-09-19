## Problem Statement

Today Fava treats forum topics as the only router: Inbox always means save, Smart Search always means search, and Telegram’s General topic is ignored. Participants who post in the “wrong” topic, drop a question in Inbox, or use General get silence, a wrong pipeline, or confusion—with no check that the message’s intent matches the topic.

The product still wants topics as clear affordances (save in Inbox, ask in Smart Search), but needs a single policy layer that understands Action Intent, refuses ambiguous work, redirects mismatches, and nudges people out of General—without turning Fava into a multi-agent runtime or opening Smart Search to the web.

## Solution

Add an **Intent Router** (Gradle module `intent-router`) that sits between the Telegram adapter and the existing ingest/search pipelines.

For a configured Catalog, every message in **Inbox**, **Smart Search**, or **General** is turned into a Fava-owned message summary and passed to the Intent Router. The router classifies Action Intent (`save` | `search` | `unclear`) with an LLM, then applies a **topic gate**:

- Intent matches the current topic → call the existing save or search pipeline.
- Intent mismatches the topic → plain-text redirect to the right topic; do not run a pipeline.
- Intent is unclear → plain-text clarify; do not run a pipeline.
- Locus is General → intent-aware nudge toward Inbox or Smart Search; never run a pipeline.
- Theme Topics stay silent (unchanged).
- If the intent LLM fails → fail closed (ask to retry; no topic fallback).

Pipelines remain ordinary services. Search stays catalog-grounded (ADR-0005); URLs in questions are allowed but the model must not claim it can open or check links. No web-search tools in this slice.

## User Stories

1. As a Participant, I want to save a link or forward in Inbox and have it filed as today, so that the happy path does not get worse.
2. As a Participant, I want to ask a Catalog Question in Smart Search and get a Catalog Answer as today, so that search keeps working.
3. As a Participant, I want Fava to detect when my Inbox message is really a search question, so that I am redirected to Smart Search instead of a failed or misleading save.
4. As a Participant, I want Fava to detect when my Smart Search message is really a save, so that I am redirected to Inbox instead of a useless search.
5. As a Participant, I want a short plain-text redirect when intent and topic disagree, so that I know exactly where to repost.
6. As a Participant, I want Fava not to run save or search on a mismatched topic, so that confirms and answers do not land in the wrong thread.
7. As a Participant, I want unclear messages in Inbox or Smart Search to get a plain-text clarify (save vs ask), so that Fava does not guess wrongly.
8. As a Participant, I want unclear handling to do nothing else until I repost clearly, so that no partial filing or answer happens by accident.
9. As a Participant, I want messages in General to get an intent-aware nudge (toward Inbox or Smart Search), so that I am not met with silence when I ignore the system topics.
10. As a Participant, I want General never to trigger filing or Smart Search answers, so that General does not become a third intake surface.
11. As a Participant posting in a Theme Topic, I want Fava to stay silent, so that theme folders remain browsing surfaces, not chat with the bot.
12. As a Participant, I want dual-action messages (e.g. “save this and also what else do I have?”) treated as unclear when the model cannot pick one action, so that Fava does not run two pipelines in one turn.
13. As a Participant, I want a clear question that happens to include a URL (e.g. “do I have similar recipes {URL}?”) to still be classifiable as search when appropriate, so that I am not forced through clarify solely because a link appears.
14. As a Participant asking with a URL in Smart Search, I want the answer grounded only in my Catalog, so that Fava does not invent web knowledge or fetch the link.
15. As a Participant asking with a URL, I want the model instructed that it cannot open or check external links, so that answers stay honest.
16. As a Participant, I want save-shaped messages in Inbox (URL and/or forward) to still go through existing normalize/reject/file behavior after the gate, so that accept rules do not move into the router.
17. As a Participant, I want search-shaped messages in Smart Search to still use existing Catalog Search after the gate, so that RAG behavior stays in search.
18. As a Catalog Owner, I want `/setup`, admin nudges, and theme-pick callbacks to bypass the Intent Router, so that setup and filing completion keep working.
19. As a Participant in an unconfigured group, I want pre-setup prompts to behave as today (not Intent Router policy), so that onboarding is unchanged.
20. As a Participant, I want DMs with Fava to bypass the Intent Router, so that `/start` onboarding stays separate from catalog routing.
21. As a Participant, I want a clear retry message when Action Intent classification fails (timeout/error), so that I know to try again rather than getting a silent drop or wrong pipeline.
22. As a Participant, I want fail-closed behavior on intent errors (no silent “assume Inbox = save”), so that availability never overrides intent safety in this slice.
23. As a developer, I want a new Gradle module `intent-router` that owns intent policy and dispatch, so that telegram does not keep accumulating product rules.
24. As a developer, I want telegram to pass a Fava-owned message summary (not Bot API DTOs) into the router, so that `intent-router` stays Telegram-free.
25. As a developer, I want the router to call ports for filing, catalog search, and participant notify, so that policy is testable with fakes.
26. As a developer, I want Action Intent prompting/parsing to live inside `intent-router` (not `classify`), so that theme classification and action intent stay separate concerns.
27. As a developer, I want domain language to say Intent Router and Action Intent (not “orchestrator/subagent runtime”), so that docs match validate-then-dispatch.
28. As a product owner, I want CONTEXT / ADR language updated when this ships (system loci, Intent Router, General as nudge-only), so that agents and humans share one vocabulary.
29. As a Participant, I want English plain-text copy for redirect, clarify, General nudge, and intent-failure, so that v1 stays consistent with existing i18n-ready messaging patterns.
30. As a demo presenter, I want wrong-topic and General paths to be demonstrable live, so that the Intent Router’s value is visible beyond the happy path.
31. As a Participant, I want matching intent + topic to feel identical to today’s success UX (filed confirm / answer + citations), so that the router is invisible when I use topics correctly.
32. As a developer, I want Theme Topics and non-locus threads to remain outside the router’s listen set, so that we do not spam nudges across every folder.
33. As a Participant, I want “thanks” / chatter in Inbox or Smart Search to land in unclear (or equivalent non-action) rather than a forced pipeline, so that small talk does not create junk saves or empty answers when the model is unsure.
34. As a product owner, I want no multi-turn agent tools and no OpenRouter web-search tool in this PRD, so that scope stays a routing layer plus prompt honesty for links.
35. As a developer, I want the modular monolith to remain one Spring Boot deployable, so that `intent-router` is a seam, not a new service.

## Implementation Decisions

### Architecture
- New Gradle module: **`intent-router`**.
- **`telegram`** maps Inbox / Smart Search / General messages for a configured Catalog into a Fava message summary (`RoutedRequest` or equivalent) and implements participant notify (Telegram replies). It no longer decides save vs search by thread id alone for those loci.
- **`intent-router`** is an application service: classify Action Intent → apply topic gate → invoke ports (filing / catalog search / notify). Deep interface: one primary entry for “handle this catalog message summary.”
- **`ingest`** and **`search`** remain callees; no multi-agent runtime.
- **`classify`** continues to own **theme** TopicClassifier only; Action Intent LLM lives in `intent-router`.
- **`app`** wires the new module; modular monolith ADR unchanged.

### Domain rules (this slice)
- Action Intent labels: `save` | `search` | `unclear`.
- Loci that enter the router: Inbox, Smart Search, General.
- Topic gate: run pipeline only when intent matches locus (save↔Inbox, search↔Smart Search).
- Mismatch → redirect notify only.
- Unclear → clarify notify only.
- General → intent-aware nudge only (never dispatch).
- Theme Topics: silent; do not enter the router.
- Dual-action / mush → `unclear` when the model cannot pick one action; no multi-pipeline turn.
- No hard rule “URL + question ⇒ unclear”; the intent model decides.
- Intent LLM error → fail closed with retry copy; no topic-default fallback.
- Bypass router: DMs, `/setup` and setup flows, theme-pick callbacks, unconfigured-group handling, non-message updates as today.

### Message summary (adapter → router)
- Catalog/chat id, locus (`INBOX` | `SMART_SEARCH` | `GENERAL`), message id, user id, text, extracted URLs, forward flag, and enough fields to build existing ingest facts when dispatching save.
- No raw Telegram types inside `intent-router`.

### Search / AI
- Catalog Answers remain grounded in Saved Items only (ADR-0005).
- If the question text includes a URL, still allow `search` when classified as such; system/prompt guidance must state the model cannot open or check external links.
- No OpenRouter web-search / browse tool in this PRD.
- Action Intent uses the existing OpenRouter-compatible stack (same ops story as other LLM calls).

### Naming
- Product/docs: **Intent Router**, **Action Intent**, message summary / routed request, loci Inbox / Smart Search / General.
- Gradle module: **`intent-router`**.

### Docs follow-through (when implementing)
- Update domain language for Intent Router and General-as-nudge-only locus.
- Amend or add ADR only if needed to record topic-gated intent routing (not required to invent web-tool policy—web is out of scope).

## Testing Decisions

### What makes a good test
- Assert **external behavior** through the Intent Router’s public seam: given a message summary + fake ports/classifier, verify which port was called (or not) and what notify outcome was requested.
- Do **not** assert prompt wording internals, private helpers, or Telegram JSON shape inside `intent-router` tests.
- Prefer fakes for Action Intent classifier, filing, search, and notify.

### What we will test
- **`intent-router` only** (agreed):
  - Match Inbox+save → filing dispatched; Match Smart Search+search → search dispatched.
  - Mismatch → redirect notify; **no** filing/search.
  - Unclear → clarify notify; **no** filing/search.
  - General + save/search intent → nudge notify; **no** filing/search.
  - Intent classifier error → fail-closed notify; **no** filing/search.
  - URL-bearing text classified as search still dispatches search (no web tool; catalog search port only).

### Explicitly not required in this PRD’s test plan
- Full `telegram` adapter mapping tests (optional follow-up).
- New `search` prompt contract tests (optional follow-up).
- End-to-end Bot API tests.

### Prior art
- Port/fake style in group update and filing/search tests: behavior-focused assertions with scripted dependencies, no live OpenRouter in unit tests.

## Out of Scope

- OpenRouter web-search / URL fetch / browse tools; amending ADR-0005 toward open-web answers.
- Executing save or search from General or from a mismatched topic (including buttons that execute).
- Button-based clarify/redirect UX (plain text only).
- Intent classification or nudges inside Theme Topics.
- Multi-action single-turn pipelines (save then search).
- Topic fallback when the intent LLM fails.
- Moving theme TopicClassifier into `intent-router`, or moving Action Intent into `classify`.
- Changing Inbox accept rules, filing, dedupe, or RAG retrieval algorithms beyond prompt honesty for links.
- Replacing forum topics as user-facing affordances.
- New networked microservice; webhook-only routing changes; membership model changes.
- Overwriting the product-wide `issues/prd.md`; this document is the feature PRD only.

## Further Notes

- Earlier working name “orchestrator/subagent” was retired in favor of **Intent Router** + ordinary ingest/search services to avoid implying an agent runtime.
- Q1 “replace topic routing” was refined: topics still **gate execution**; the router **validates** intent against locus rather than freely executing anywhere.
- Existing product PRD (`issues/prd.md`) remains the catalog/setup/save/search baseline; this PRD layers intent-gated routing on top.
- Implementation should keep `GroupUpdateHandler` as a thin adapter once the router owns policy for the three loci.
`)