package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessageEntity(String type, int offset, int length, String url) {

	public TelegramMessageEntity(String type, int offset, int length) {
		this(type, offset, length, null);
	}
}
