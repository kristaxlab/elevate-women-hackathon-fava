package com.fava.classify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenRouter / OpenAI-compatible chat settings ({@code fava.openrouter.*}).
 */
@ConfigurationProperties(prefix = "fava.openrouter")
public record OpenRouterProperties(String apiKey, String baseUrl, String chatModel) {

	public OpenRouterProperties {
		apiKey = apiKey == null ? "" : apiKey;
		baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://openrouter.ai/api/v1" : baseUrl;
		chatModel = (chatModel == null || chatModel.isBlank()) ? "openai/gpt-4o-mini" : chatModel;
	}

	public boolean hasApiKey() {
		return !apiKey.isBlank();
	}
}
