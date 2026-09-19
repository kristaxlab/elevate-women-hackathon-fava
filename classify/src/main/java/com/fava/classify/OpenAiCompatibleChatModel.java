package com.fava.classify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI-compatible chat completions client (OpenRouter or any replaceable base URL).
 */
public final class OpenAiCompatibleChatModel implements ChatModelPort {

	private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatModel.class);

	private final String apiKey;
	private final String baseUrl;
	private final String model;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public OpenAiCompatibleChatModel(String apiKey, String baseUrl, String model, ObjectMapper objectMapper) {
		this(
				apiKey,
				baseUrl,
				model,
				objectMapper,
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
	}

	OpenAiCompatibleChatModel(
			String apiKey,
			String baseUrl,
			String model,
			ObjectMapper objectMapper,
			HttpClient httpClient) {
		this.apiKey = apiKey;
		this.baseUrl = trimTrailingSlash(baseUrl == null ? "" : baseUrl);
		this.model = model;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
	}

	@Override
	public String complete(String systemPrompt, String userMessage) {
		try {
			ChatRequest body = new ChatRequest(
					model,
					List.of(
							new ChatMessage("system", systemPrompt),
							new ChatMessage("user", userMessage)),
					0.0);
			String json = objectMapper.writeValueAsString(body);
			HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
					.timeout(Duration.ofSeconds(60))
					.header("Content-Type", "application/json")
					.header("Authorization", "Bearer " + apiKey)
					.POST(HttpRequest.BodyPublishers.ofString(json))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException(
						"chat/completions HTTP " + response.statusCode() + ": " + response.body());
			}
			ChatResponse parsed = objectMapper.readValue(response.body(), ChatResponse.class);
			if (parsed.choices() == null || parsed.choices().isEmpty() || parsed.choices().getFirst().message() == null) {
				throw new IllegalStateException("chat/completions missing choices: " + response.body());
			}
			String content = parsed.choices().getFirst().message().content();
			return content == null ? "" : content;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("chat/completions interrupted", e);
		}
		catch (IOException e) {
			log.warn("chat/completions failed: {}", e.toString());
			throw new IllegalStateException("chat/completions failed: " + e, e);
		}
	}

	private static String trimTrailingSlash(String url) {
		if (url.endsWith("/")) {
			return url.substring(0, url.length() - 1);
		}
		return url;
	}

	private record ChatRequest(String model, List<ChatMessage> messages, double temperature) {
	}

	private record ChatMessage(String role, String content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ChatResponse(List<Choice> choices) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Choice(ChatMessageBody message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ChatMessageBody(String content) {
	}
}
