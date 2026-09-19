package com.fava.catalog;

/**
 * A row in the embedding-models registry.
 */
public record EmbeddingModel(
		Long id,
		String modelId,
		int dimensions,
		boolean active,
		CatalogSyncStatus catalogSyncStatus) {
}
