package com.fava.telegram;

/**
 * Holds the bot's Telegram user id after {@code getMe} (or config). Used to detect admin promotions.
 */
public final class BotUserIdHolder {

	private volatile Long userId;

	public Long get() {
		return userId;
	}

	public void set(Long userId) {
		this.userId = userId;
	}
}
