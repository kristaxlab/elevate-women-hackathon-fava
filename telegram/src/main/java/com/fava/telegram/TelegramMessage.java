package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
		@JsonProperty("message_id") long messageId,
		TelegramChat chat,
		String text,
		List<TelegramMessageEntity> entities) {
}
