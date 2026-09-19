package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramChat(
		long id,
		String type,
		@JsonProperty("is_forum") Boolean isForum) {

	public TelegramChat(long id, String type) {
		this(id, type, null);
	}
}
