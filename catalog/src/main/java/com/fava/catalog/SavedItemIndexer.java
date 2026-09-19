package com.fava.catalog;

/**
 * Indexes a Saved Item into the Catalog embedding store (or no-ops when embeddings are unavailable).
 */
public interface SavedItemIndexer {

	void index(SavedItem item);
}
