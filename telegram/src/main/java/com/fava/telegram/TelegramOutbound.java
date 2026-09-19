package com.fava.telegram;

import java.util.List;

/**
 * Outbound seam for Telegram Bot API sends. Fake this in tests.
 */
public interface TelegramOutbound {

	void sendText(long chatId, String text);

	void sendTextWithInlineKeyboard(long chatId, String text, List<InlineUrlButton> buttons);
}
