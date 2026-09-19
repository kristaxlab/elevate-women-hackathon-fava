package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
		@JsonProperty("message_id") long messageId,
		TelegramChat chat,
		TelegramUser from,
		String text,
		String caption,
		List<TelegramMessageEntity> entities,
		@JsonProperty("caption_entities") List<TelegramMessageEntity> captionEntities,
		@JsonProperty("message_thread_id") Long messageThreadId,
		@JsonProperty("forward_date") Integer forwardDate,
		@JsonProperty("forward_from") TelegramUser forwardFrom,
		@JsonProperty("forward_origin") TelegramForwardOrigin forwardOrigin) {

	public TelegramMessage(long messageId, TelegramChat chat, String text, List<TelegramMessageEntity> entities) {
		this(messageId, chat, null, text, null, entities, null, null, null, null, null);
	}

	public TelegramMessage(
			long messageId,
			TelegramChat chat,
			TelegramUser from,
			String text,
			List<TelegramMessageEntity> entities,
			Long messageThreadId) {
		this(messageId, chat, from, text, null, entities, null, messageThreadId, null, null, null);
	}

	public boolean isForwarded() {
		return forwardOrigin != null || forwardFrom != null || forwardDate != null;
	}

	public String bodyText() {
		if (text != null && !text.isBlank()) {
			return text;
		}
		return caption;
	}
}
