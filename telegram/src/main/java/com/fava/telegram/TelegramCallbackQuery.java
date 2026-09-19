package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram {@code CallbackQuery} for inline button presses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramCallbackQuery(
		String id,
		TelegramUser from,
		TelegramMessage message,
		@JsonProperty("chat_instance") String chatInstance,
		String data) {
}
