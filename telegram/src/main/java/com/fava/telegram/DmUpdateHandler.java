package com.fava.telegram;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;

/**
 * Routes private-chat DMs: {@code /start} welcome and a harmless fallback for other text.
 * Non-private chats and non-message updates are ignored.
 */
public final class DmUpdateHandler {

	static final String MSG_START = "fava.dm.start";
	static final String MSG_FALLBACK = "fava.dm.fallback";

	private final MessageSource messages;
	private final TelegramOutbound outbound;

	public DmUpdateHandler(MessageSource messages, TelegramOutbound outbound) {
		this.messages = messages;
		this.outbound = outbound;
	}

	public void handle(TelegramUpdate update) {
		if (update == null || update.message() == null) {
			return;
		}
		TelegramMessage message = update.message();
		TelegramChat chat = message.chat();
		if (chat == null || !"private".equals(chat.type())) {
			return;
		}
		if (isStartCommand(message)) {
			outbound.sendText(chat.id(), messages.getMessage(MSG_START, null, Locale.ENGLISH));
			return;
		}
		if (message.text() != null && !message.text().isBlank()) {
			outbound.sendText(chat.id(), messages.getMessage(MSG_FALLBACK, null, Locale.ENGLISH));
		}
	}

	private static boolean isStartCommand(TelegramMessage message) {
		String text = message.text();
		if (text == null || text.isBlank()) {
			return false;
		}
		List<TelegramMessageEntity> entities = message.entities();
		if (entities != null) {
			for (TelegramMessageEntity entity : entities) {
				if ("bot_command".equals(entity.type()) && entity.offset() == 0) {
					int end = Math.min(entity.length(), text.length());
					return isStartToken(text.substring(0, end));
				}
			}
		}
		String firstToken = text.split("\\s+", 2)[0];
		return isStartToken(firstToken);
	}

	private static boolean isStartToken(String token) {
		return "/start".equals(token) || token.startsWith("/start@");
	}
}
