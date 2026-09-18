package com.fava.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fava.telegram")
public record TelegramProperties(String botToken) {

	boolean hasToken() {
		return botToken != null && !botToken.isBlank();
	}
}
