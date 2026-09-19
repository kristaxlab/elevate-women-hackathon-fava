package com.fava.classify;

/**
 * Outcome of choosing a Theme Topic for a Saved Item draft.
 */
public sealed interface ClassifierDecision {

	record Confident(String themeName) implements ClassifierDecision {
	}

	record NeedsUserPick() implements ClassifierDecision {
	}
}
