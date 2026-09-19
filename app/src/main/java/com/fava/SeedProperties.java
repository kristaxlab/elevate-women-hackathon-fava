package com.fava;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seed-only Catalog loader settings ({@code fava.seed.*}). Off by default.
 */
@ConfigurationProperties(prefix = "fava.seed")
public record SeedProperties(boolean enabled, String chatId) {

	public SeedProperties {
		chatId = chatId == null ? "" : chatId.trim();
	}

	public long requireChatId() {
		if (chatId.isBlank()) {
			throw new IllegalStateException(
					"fava.seed.enabled=true requires fava.seed.chat-id (FAVA_SEED_CHAT_ID)");
		}
		try {
			return Long.parseLong(chatId);
		}
		catch (NumberFormatException e) {
			throw new IllegalStateException("fava.seed.chat-id must be a Telegram chat id long: " + chatId, e);
		}
	}
}
