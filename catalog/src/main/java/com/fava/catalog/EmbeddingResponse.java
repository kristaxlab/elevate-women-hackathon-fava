package com.fava.catalog;

/**
 * Nested embedding payload for developer HTTP responses.
 */
public record EmbeddingResponse(String modelId, int dimensions, float[] vector) {

	public static EmbeddingResponse from(StoredEmbedding stored) {
		return new EmbeddingResponse(stored.modelId(), stored.dimensions(), stored.vector());
	}
}
