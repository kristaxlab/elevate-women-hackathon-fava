package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fava.catalog.CatalogForumPort;
import com.fava.ingest.FilingCallbackButton;
import com.fava.ingest.FilingPort;
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
 * Thin Bot API client using JDK HttpClient (long-poll getUpdates + messaging + forum/admin helpers).
 */
final class TelegramBotClient implements TelegramOutbound, CatalogForumPort, ChatAdminPort, FilingPort {

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
	 * Resolves this bot's identity via {@code getMe}.
	 */
	BotIdentity getMe() throws IOException, InterruptedException {
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
		username = username == null || username.isBlank() ? null : username;
		return new BotIdentity(parsed.result().id(), username);
	}

	String getMeUsername() throws IOException, InterruptedException {
		return getMe().username();
	}

	@Override
	public boolean isForum(long chatId) {
		try {
			String body = objectMapper.writeValueAsString(new ChatIdBody(chatId));
			HttpResponse<String> response = postJson("getChat", body);
			if (response.statusCode() != 200) {
				log.warn("getChat failed HTTP {}: {}", response.statusCode(), response.body());
				return false;
			}
			GetChatResponse parsed = objectMapper.readValue(response.body(), GetChatResponse.class);
			if (!parsed.ok() || parsed.result() == null) {
				log.warn("getChat not ok: {}", response.body());
				return false;
			}
			return Boolean.TRUE.equals(parsed.result().isForum());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("getChat interrupted for chat {}", chatId);
			return false;
		}
		catch (IOException e) {
			log.warn("getChat failed for chat {}: {}", chatId, e.toString());
			return false;
		}
	}

	@Override
	public long createForumTopic(long chatId, String name) {
		try {
			String body = objectMapper.writeValueAsString(new CreateForumTopicBody(chatId, name));
			HttpResponse<String> response = postJson("createForumTopic", body);
			if (response.statusCode() != 200) {
				throw new IllegalStateException(
						"createForumTopic HTTP " + response.statusCode() + ": " + response.body());
			}
			CreateForumTopicResponse parsed = objectMapper.readValue(response.body(), CreateForumTopicResponse.class);
			if (!parsed.ok() || parsed.result() == null) {
				throw new IllegalStateException("createForumTopic not ok: " + response.body());
			}
			return parsed.result().messageThreadId();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("createForumTopic interrupted for chat " + chatId, e);
		}
		catch (IOException e) {
			throw new IllegalStateException("createForumTopic failed for chat " + chatId + ": " + e, e);
		}
	}

	@Override
	public boolean isAdmin(long chatId, long userId) {
		try {
			String body = objectMapper.writeValueAsString(new GetChatMemberBody(chatId, userId));
			HttpResponse<String> response = postJson("getChatMember", body);
			if (response.statusCode() != 200) {
				log.warn("getChatMember failed HTTP {}: {}", response.statusCode(), response.body());
				return false;
			}
			GetChatMemberResponse parsed = objectMapper.readValue(response.body(), GetChatMemberResponse.class);
			if (!parsed.ok() || parsed.result() == null) {
				log.warn("getChatMember not ok: {}", response.body());
				return false;
			}
			String status = parsed.result().status();
			return "administrator".equals(status) || "creator".equals(status);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("getChatMember interrupted for chat {}", chatId);
			return false;
		}
		catch (IOException e) {
			log.warn("getChatMember failed for chat {}: {}", chatId, e.toString());
			return false;
		}
	}

	@Override
	public void sendText(long chatId, String text) {
		sendMessage(chatId, text, null);
	}

	@Override
	public void sendTextWithInlineKeyboard(long chatId, String text, List<InlineUrlButton> buttons) {
		List<List<InlineKeyboardButtonBody>> rows = buttons.stream()
				.map(b -> List.of(InlineKeyboardButtonBody.url(b.text(), b.url())))
				.toList();
		sendMessage(chatId, text, null, new InlineKeyboardMarkup(rows));
	}

	@Override
	public void replyText(long chatId, long replyToMessageId, String text) {
		sendMessage(chatId, text, replyToMessageId, null);
	}

	@Override
	public void replyTextWithCallbackButtons(
			long chatId, long replyToMessageId, String text, List<InlineCallbackButton> buttons) {
		List<List<InlineKeyboardButtonBody>> rows = buttons.stream()
				.map(b -> List.of(InlineKeyboardButtonBody.callback(b.text(), b.callbackData())))
				.toList();
		sendMessage(chatId, text, replyToMessageId, new InlineKeyboardMarkup(rows));
	}

	@Override
	public void replyWithCallbackButtons(
			long chatId, long replyToMessageId, String text, List<FilingCallbackButton> buttons) {
		List<InlineCallbackButton> mapped = buttons.stream()
				.map(b -> new InlineCallbackButton(b.label(), b.callbackData()))
				.toList();
		replyTextWithCallbackButtons(chatId, replyToMessageId, text, mapped);
	}

	@Override
	public void answerCallbackQuery(String callbackQueryId) {
		try {
			String body = objectMapper.writeValueAsString(new AnswerCallbackQueryBody(callbackQueryId));
			HttpResponse<String> response = postJson("answerCallbackQuery", body);
			if (response.statusCode() != 200) {
				log.warn("answerCallbackQuery failed HTTP {}: {}", response.statusCode(), response.body());
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("answerCallbackQuery interrupted");
		}
		catch (IOException e) {
			log.warn("answerCallbackQuery failed: {}", e.toString());
		}
	}

	@Override
	public void replyToMessage(long chatId, long replyToMessageId, String text) {
		replyText(chatId, replyToMessageId, text);
	}

	@Override
	public void copyMessage(long chatId, long fromMessageId, long toMessageThreadId) {
		copyMessageToThread(chatId, fromMessageId, toMessageThreadId);
	}

	@Override
	public void copyMessageToThread(long chatId, long fromMessageId, long messageThreadId) {
		try {
			String body = objectMapper.writeValueAsString(
					new CopyMessageBody(chatId, chatId, fromMessageId, messageThreadId));
			HttpResponse<String> response = postJson("copyMessage", body);
			if (response.statusCode() != 200) {
				log.warn("copyMessage failed HTTP {}: {}", response.statusCode(), response.body());
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("copyMessage interrupted for chat {}", chatId);
		}
		catch (IOException e) {
			log.warn("copyMessage failed for chat {}: {}", chatId, e.toString());
		}
	}

	private void sendMessage(long chatId, String text, Long replyToMessageId, InlineKeyboardMarkup replyMarkup) {
		try {
			String body = objectMapper.writeValueAsString(
					new SendMessageBody(chatId, text, replyToMessageId, replyMarkup));
			HttpResponse<String> response = postJson("sendMessage", body);
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

	private void sendMessage(long chatId, String text, InlineKeyboardMarkup replyMarkup) {
		sendMessage(chatId, text, null, replyMarkup);
	}

	private HttpResponse<String> postJson(String method, String body) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + token + "/" + method))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	record BotIdentity(long id, String username) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GetUpdatesResponse(boolean ok, List<TelegramUpdate> result) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GetMeResponse(boolean ok, GetMeUser result) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GetMeUser(long id, String username) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GetChatResponse(boolean ok, TelegramChat result) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GetChatMemberResponse(boolean ok, TelegramChatMember result) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CreateForumTopicResponse(boolean ok, ForumTopic result) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ForumTopic(@JsonProperty("message_thread_id") long messageThreadId) {
	}

	private record ChatIdBody(@JsonProperty("chat_id") long chatId) {
	}

	private record GetChatMemberBody(
			@JsonProperty("chat_id") long chatId,
			@JsonProperty("user_id") long userId) {
	}

	private record CreateForumTopicBody(
			@JsonProperty("chat_id") long chatId,
			String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record SendMessageBody(
			@JsonProperty("chat_id") long chatId,
			String text,
			@JsonProperty("reply_to_message_id") Long replyToMessageId,
			@JsonProperty("reply_markup") InlineKeyboardMarkup replyMarkup) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record CopyMessageBody(
			@JsonProperty("chat_id") long chatId,
			@JsonProperty("from_chat_id") long fromChatId,
			@JsonProperty("message_id") long messageId,
			@JsonProperty("message_thread_id") long messageThreadId) {
	}

	private record AnswerCallbackQueryBody(@JsonProperty("callback_query_id") String callbackQueryId) {
	}

	private record InlineKeyboardMarkup(@JsonProperty("inline_keyboard") List<List<InlineKeyboardButtonBody>> inlineKeyboard) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record InlineKeyboardButtonBody(
			String text,
			String url,
			@JsonProperty("callback_data") String callbackData) {

		static InlineKeyboardButtonBody url(String text, String url) {
			return new InlineKeyboardButtonBody(text, url, null);
		}

		static InlineKeyboardButtonBody callback(String text, String callbackData) {
			return new InlineKeyboardButtonBody(text, null, callbackData);
		}
	}
}
