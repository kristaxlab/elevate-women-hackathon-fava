package com.fava.ingest;

/**
 * Outcome of normalizing an Inbox message into a Saved Item draft.
 */
public sealed interface NormalizeResult {

	record Accepted(AcceptedDraft draft) implements NormalizeResult {
	}

	record Rejected(String reason) implements NormalizeResult {
	}
}
