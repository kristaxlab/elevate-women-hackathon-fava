package com.fava.catalog;

import java.util.Optional;

/**
 * Outcome of creating a Saved Item via the developer HTTP write path.
 */
public sealed interface SavedItemCreateResult {

	record Created(SavedItem item, StoredEmbedding embedding) implements SavedItemCreateResult {
		public Created {
			if (item == null || embedding == null) {
				throw new IllegalArgumentException("item and embedding are required");
			}
		}
	}

	record Duplicate(SavedItem item, Optional<StoredEmbedding> embedding) implements SavedItemCreateResult {
		public Duplicate {
			if (item == null) {
				throw new IllegalArgumentException("item is required");
			}
			embedding = embedding == null ? Optional.empty() : embedding;
		}
	}
}
