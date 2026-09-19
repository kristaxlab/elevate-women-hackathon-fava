package com.fava.telegram;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the bot's Telegram username for deep links. Filled from config or {@code getMe} at poller start.
 */
final class BotUsernameHolder {

	private final AtomicReference<String> username = new AtomicReference<>();

	void set(String value) {
		if (value == null || value.isBlank()) {
			username.set(null);
			return;
		}
		String trimmed = value.startsWith("@") ? value.substring(1) : value.trim();
		username.set(trimmed.isBlank() ? null : trimmed);
	}

	String get() {
		return username.get();
	}
}
