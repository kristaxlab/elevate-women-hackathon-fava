package com.fava.catalog;

/**
 * A row in the embedding-models registry.
 *
 * @param modelId OpenRouter / OpenAI-compatible embedding model id
 *                (same value as {@code fava.openrouter.embedding-model})
 */
public record EmbeddingModel(
		Long id,
		String modelId,
		int dimensions,
		boolean active,
		CatalogSyncStatus catalogSyncStatus) {
}
