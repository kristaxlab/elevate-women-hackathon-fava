package com.fava.catalog;

import com.fava.classify.OpenRouterProperties;

/**
 * Configured embedding space: model id and vector dimensions that must stay in sync.
 */
public record EmbeddingSpace(String modelId, int dimensions) {

	public static final EmbeddingSpace DEFAULT =
			new EmbeddingSpace("openai/text-embedding-3-small", EmbeddingDimensions.DEFAULT);

	public EmbeddingSpace {
		if (modelId == null || modelId.isBlank()) {
			throw new IllegalArgumentException("modelId must be non-blank");
		}
		if (dimensions <= 0) {
			throw new IllegalArgumentException("dimensions must be positive");
		}
		modelId = modelId.trim();
	}

	public static EmbeddingSpace from(OpenRouterProperties properties) {
		return new EmbeddingSpace(properties.embeddingModel(), properties.embeddingDimensions());
	}

	public boolean differsFrom(EmbeddingModel model) {
		return !modelId.equals(model.modelId()) || dimensions != model.dimensions();
	}
}
