package com.fava.catalog;

import java.util.Optional;

/**
 * Registry of embedding models used for Catalog vector search. Exactly one may be active.
 */
public interface EmbeddingModelRegistry {

	Optional<EmbeddingModel> findActive();

	/**
	 * Inserts {@code space} as the sole active model (deactivates any prior active).
	 */
	EmbeddingModel activate(EmbeddingSpace space, CatalogSyncStatus status);

	void updateSyncStatus(long id, CatalogSyncStatus status);
}
