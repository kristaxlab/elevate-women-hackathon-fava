package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * Thin Bot API client using JDK HttpClient (long-poll getUpdates + sendMessage + getMe).
 */
final class TelegramBotClient implements TelegramOutbound {

	private static final Logger log = LoggerFactory.getLogger(TelegramBotClient.class);
	private static final String API_BASE = "https://api.telegram.org/bot";

	private final String token;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	TelegramBotClient(String token, ObjectMapper objectMapper) {
		this(token, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
	}

	TelegramBotClient(String token, ObjectMapper objectMapper, HttpClient httpClient) {
		this.token = token;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
	}

	List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) throws IOException, InterruptedException {
		String url = API_BASE + token + "/getUpdates?offset=" + offset + "&timeout=" + timeoutSeconds;
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(timeoutSeconds + 10L))
				.GET()
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("getUpdates HTTP " + response.statusCode() + ": " + response.body());
		}
		GetUpdatesResponse parsed = objectMapper.readValue(response.body(), GetUpdatesResponse.class);
		if (!parsed.ok()) {
			throw new IOException("getUpdates not ok: " + response.body());
		}
		return parsed.result() == null ? List.of() : parsed.result();
	}

	/**
	 * Resolves this bot's username via {@code getMe}. Returns null if missing or the call fails.
	 */
	String getMeUsername() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + token + "/getMe"))
				.timeout(Duration.ofSeconds(30))
				.GET()
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("getMe HTTP " + response.statusCode() + ": " + response.body());
		}
		GetMeResponse parsed = objectMapper.readValue(response.body(), GetMeResponse.class);
		if (!parsed.ok() || parsed.result() == null) {
			throw new IOException("getMe not ok: " + response.body());
		}
		String username = parsed.result().username();
		return username == null || username.isBlank() ? null : username;
	}

	@Override
	public void sendText(long chatId, String text) {
		sendMessage(chatId, text, null);
	}

	@Override
	public void sendTextWithInlineKeyboard(long chatId, String text, List<InlineUrlButton> buttons) {
		List<List<InlineKeyboardButtonBody>> rows = buttons.stream()
				.map(b -> List.of(new InlineKeyboardButtonBody(b.text(), b.url())))
				.toList();
		sendMessage(chatId, text, new InlineKeyboardMarkup(rows));
	}

	private void sendMessage(long chatId, String text, InlineKeyboardMarkup replyMarkup) {
		try {
			String body = objectMapper.writeValueAsString(new SendMessageBody(chatId, text, replyMarkup));
			HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + token + "/sendMessage"))
					.timeout(Duration.ofSeconds(30))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				log.warn("sendMessage failed HTTP {}: {}", response.statusCode(), response.body());
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("sendMessage interrupted for chat {}", chatId);
		}
		catch (IOException e) {
			log.warn("sendMessage failed for chat {}: {}", chatId, e.toString());
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GetUpdatesResponse(boolean ok, List<TelegramUpdate> result) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GetMeResponse(boolean ok, GetMeUser result) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GetMeUser(String username) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record SendMessageBody(
			@JsonProperty("chat_id") long chatId,
			String text,
			@JsonProperty("reply_markup") InlineKeyboardMarkup replyMarkup) {
	}

	private record InlineKeyboardMarkup(@JsonProperty("inline_keyboard") List<List<InlineKeyboardButtonBody>> inlineKeyboard) {
	}

	private record InlineKeyboardButtonBody(String text, String url) {
	}
}
