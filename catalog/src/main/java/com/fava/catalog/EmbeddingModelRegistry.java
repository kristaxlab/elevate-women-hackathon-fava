package com.fava.catalog;

import java.util.Optional;

/**
 * Registry of embedding models used for Catalog vector search. Exactly one may be active.
 */
public interface EmbeddingModelRegistry {

	Optional<EmbeddingModel> findActive();

	/**
	 * Inserts {@code modelId}/{@code dimensions} as the sole active model (deactivates any prior active).
	 */
	EmbeddingModel activate(String modelId, int dimensions, CatalogSyncStatus status);

	void updateSyncStatus(long id, CatalogSyncStatus status);
}
