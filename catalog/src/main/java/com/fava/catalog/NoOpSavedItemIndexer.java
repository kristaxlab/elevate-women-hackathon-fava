package com.fava.catalog;

/**
 * No-op indexer for tests and environments without embedding configuration.
 */
public final class NoOpSavedItemIndexer implements SavedItemIndexer {

	@Override
	public void index(SavedItem item) {
		// intentionally empty
	}
}
