package com.fava.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fava.telegram")
public record TelegramProperties(String botToken, String botUsername) {

	boolean hasToken() {
		return botToken != null && !botToken.isBlank();
	}

	boolean hasConfiguredUsername() {
		return botUsername != null && !botUsername.isBlank();
	}
}
