package com.fava.telegram;

import java.util.List;

/**
 * Outbound seam for Telegram Bot API sends. Fake this in tests.
 */
public interface TelegramOutbound {

	void sendText(long chatId, String text);

	void sendTextWithInlineKeyboard(long chatId, String text, List<InlineUrlButton> buttons);

	void replyText(long chatId, long replyToMessageId, String text);

	void copyMessage(long chatId, long fromMessageId, long toMessageThreadId);
}
