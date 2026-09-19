package com.fava.classify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenRouter / OpenAI-compatible chat and embedding settings ({@code fava.openrouter.*}).
 *
 * <p>Default embedding model/dimensions match catalog {@code EmbeddingSpace.DEFAULT}
 * ({@code openai/text-embedding-3-small}, 1536).
 */
@ConfigurationProperties(prefix = "fava.openrouter")
public record OpenRouterProperties(
		String apiKey,
		String baseUrl,
		String chatModel,
		String embeddingModel,
		Integer embeddingDimensions) {

	private static final String DEFAULT_EMBEDDING_MODEL = "openai/text-embedding-3-small";
	private static final int DEFAULT_EMBEDDING_DIMENSIONS = 1536;

	public OpenRouterProperties {
		apiKey = apiKey == null ? "" : apiKey;
		baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://openrouter.ai/api/v1" : baseUrl;
		chatModel = (chatModel == null || chatModel.isBlank()) ? "openai/gpt-4o-mini" : chatModel;
		embeddingModel = (embeddingModel == null || embeddingModel.isBlank())
				? DEFAULT_EMBEDDING_MODEL
				: embeddingModel;
		embeddingDimensions = (embeddingDimensions == null || embeddingDimensions <= 0)
				? DEFAULT_EMBEDDING_DIMENSIONS
				: embeddingDimensions;
	}

	public boolean hasApiKey() {
		return !apiKey.isBlank();
	}
}
