package com.fava.classify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenRouter / OpenAI-compatible chat and embedding settings ({@code fava.openrouter.*}).
 */
@ConfigurationProperties(prefix = "fava.openrouter")
public record OpenRouterProperties(
		String apiKey,
		String baseUrl,
		String chatModel,
		String embeddingModel,
		Integer embeddingDimensions) {

	public OpenRouterProperties {
		apiKey = apiKey == null ? "" : apiKey;
		baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://openrouter.ai/api/v1" : baseUrl;
		chatModel = (chatModel == null || chatModel.isBlank()) ? "openai/gpt-4o-mini" : chatModel;
		embeddingModel = (embeddingModel == null || embeddingModel.isBlank())
				? "openai/text-embedding-3-small"
				: embeddingModel;
		embeddingDimensions = (embeddingDimensions == null || embeddingDimensions <= 0)
				? 1536
				: embeddingDimensions;
	}

	public boolean hasApiKey() {
		return !apiKey.isBlank();
	}
}
