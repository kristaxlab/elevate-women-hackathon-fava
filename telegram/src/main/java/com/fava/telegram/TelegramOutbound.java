package com.fava.telegram;

/**
 * Outbound seam for Telegram Bot API sends. Fake this in tests.
 */
public interface TelegramOutbound {

	void sendText(long chatId, String text);
}
