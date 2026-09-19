# Fava

Personal favorites catalog: people save links and Telegram posts into a Telegram forum group, file them under themes, and ask natural-language questions answered only from what they saved.

## Language

### Product & people

**Fava**:
The Telegram bot and product that hosts personal catalogs.
_Avoid_: the app, the service (when referring to the product people talk to)

**Catalog Owner**:
The person who creates or attaches a Catalog and runs setup; usually a Telegram group admin.
_Avoid_: account holder, tenant, customer

**Participant**:
Anyone who can post in the Catalog’s Telegram group and therefore can save items or ask Smart Search questions. Fava does not model membership beyond Telegram’s own access.
_Avoid_: member (as a Fava role), collaborator, teammate, invitee

### Catalog structure

**Catalog**:
A personal collection of Saved Items bound to exactly one forum-enabled Telegram supergroup.
_Avoid_: library, vault, collection, workspace, channel

**Theme Topic**:
A user-defined filing category within a Catalog (e.g. AI, Fitness, or a project name). Each Theme Topic has a corresponding Telegram forum topic used as its visible folder.
_Avoid_: tag, label, folder, category, channel section

**System Topic**:
A bot-created forum topic that is not a Theme Topic. In v1: Inbox and Smart Search.
_Avoid_: special folder, meta topic

**Inbox**:
The System Topic where Participants post candidates to **save**. The Intent Router runs a pipeline here only when Action Intent is save; search-shaped messages are redirected to Smart Search.
_Avoid_: queue, intake

**Smart Search**:
The System Topic where Participants ask Catalog Questions. The Intent Router runs search here only when Action Intent is search; save-shaped messages are redirected to Inbox.
_Avoid_: chat, assistant topic, RAG topic

**General**:
Telegram’s default forum topic (not a Fava System Topic). The Intent Router may nudge Participants toward Inbox or Smart Search based on Action Intent; it never saves or answers there.
_Avoid_: treating General as a third intake folder

**Intent Router**:
The policy layer that classifies Action Intent for messages in Inbox, Smart Search, or General, applies the topic gate, and dispatches to ingest or Smart Search (or notifies only).
_Avoid_: orchestrator, multi-agent runtime, subagent (for these Java pipelines)

**Action Intent**:
The classified request type for a Catalog message: save, search, or unclear.
_Avoid_: theme classification (see Classifier Decision), routing (alone)

**Setup**:
The one-time configuration of a Catalog’s Theme Topics after Fava is admin in a forum group. v1 does not allow changing Theme Topics afterward.
_Avoid_: onboarding (for the in-group topic step), configuration wizard

### Saved content

**Saved Item**:
One unit of content stored in a Catalog: optional URL plus text captured from Telegram (caption, link preview, and/or forwarded post text), assigned to exactly one Theme Topic.
_Avoid_: document, bookmark, entry, content item, post (alone), link (alone)

**Source Message**:
The Telegram message in Inbox that triggered ingest of a Saved Item.
_Avoid_: update, event, raw message

**Filing**:
The assignment of a Saved Item to exactly one Theme Topic, including copying it into that theme’s forum topic and confirming on the Source Message.
_Avoid_: tagging, sorting, routing, classification (as the user-visible act; see Classifier Decision for the AI step)

**Classifier Decision**:
The outcome of choosing a Theme Topic for a Saved Item: either a single Theme Topic or a request that the Participant pick via buttons when confidence is low.
_Avoid_: prediction, label, category score

**Duplicate**:
A new Inbox candidate whose URL already exists as a Saved Item in the same Catalog. It is not filed again.
_Avoid_: conflict, merge

### Search

**Catalog Question**:
A free-text question asked in Smart Search, answered using only Saved Items from that Catalog.
_Avoid_: prompt, query (when talking to users), chat message

**Catalog Answer**:
Fava’s reply to a Catalog Question: a natural-language answer grounded in Saved Items, plus Citations.
_Avoid_: completion, hallucination (except when describing failure modes), search results (alone)

**Citation**:
A pointer from a Catalog Answer back to a Saved Item (short snippet/title and URL when present) so the Participant can open the original save.
_Avoid_: source, reference, hit, search result
