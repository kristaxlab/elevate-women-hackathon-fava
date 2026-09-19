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
		List<TelegramMessageEntity> entities,
		@JsonProperty("message_thread_id") Long messageThreadId) {

	public TelegramMessage(long messageId, TelegramChat chat, String text, List<TelegramMessageEntity> entities) {
		this(messageId, chat, null, text, entities, null);
	}
}
