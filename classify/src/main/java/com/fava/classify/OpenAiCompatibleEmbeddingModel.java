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
 * OpenAI-compatible embeddings client (OpenRouter or any replaceable base URL).
 */
public final class OpenAiCompatibleEmbeddingModel implements EmbeddingPort {

	private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingModel.class);

	private final String apiKey;
	private final String baseUrl;
	private final String model;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public OpenAiCompatibleEmbeddingModel(String apiKey, String baseUrl, String model, ObjectMapper objectMapper) {
		this(
				apiKey,
				baseUrl,
				model,
				objectMapper,
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
	}

	OpenAiCompatibleEmbeddingModel(
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
	public float[] embed(String text) {
		try {
			EmbeddingRequest body = new EmbeddingRequest(model, text);
			String json = objectMapper.writeValueAsString(body);
			HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/embeddings"))
					.timeout(Duration.ofSeconds(60))
					.header("Content-Type", "application/json")
					.header("Authorization", "Bearer " + apiKey)
					.POST(HttpRequest.BodyPublishers.ofString(json))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException(
						"embeddings HTTP " + response.statusCode() + ": " + response.body());
			}
			EmbeddingResponse parsed = objectMapper.readValue(response.body(), EmbeddingResponse.class);
			if (parsed.data() == null || parsed.data().isEmpty() || parsed.data().getFirst().embedding() == null) {
				throw new IllegalStateException("embeddings missing data: " + response.body());
			}
			List<Double> values = parsed.data().getFirst().embedding();
			float[] vector = new float[values.size()];
			for (int i = 0; i < values.size(); i++) {
				vector[i] = values.get(i).floatValue();
			}
			return vector;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("embeddings interrupted", e);
		}
		catch (IOException e) {
			log.warn("embeddings failed: {}", e.toString());
			throw new IllegalStateException("embeddings failed: " + e, e);
		}
	}

	private static String trimTrailingSlash(String url) {
		if (url.endsWith("/")) {
			return url.substring(0, url.length() - 1);
		}
		return url;
	}

	private record EmbeddingRequest(String model, String input) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record EmbeddingResponse(List<EmbeddingData> data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record EmbeddingData(List<Double> embedding) {
	}
}
