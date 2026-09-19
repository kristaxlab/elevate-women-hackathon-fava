package com.fava.search;

/**
 * Builds a Telegram Theme Topic deep link from a Catalog chat id and Theme Topic message id.
 *
 * @see <a href="https://core.telegram.org/api/links">Telegram deep links</a>
 */
public final class ThemeTopicDeepLink {

	private ThemeTopicDeepLink() {
	}

	/**
	 * Private-supergroup message link {@code https://t.me/c/<internal_id>/<message_id>}.
	 * Supergroup chat ids are {@code -100xxxxxxxxxx}; the link uses the digits after {@code -100}.
	 */
	public static String forMessage(long chatId, String messageId) {
		if (messageId == null || messageId.isBlank()) {
			throw new IllegalArgumentException("messageId must not be blank");
		}
		return "https://t.me/c/" + privateLinkChatId(chatId) + "/" + messageId.trim();
	}

	static String privateLinkChatId(long chatId) {
		String raw = Long.toString(chatId);
		if (raw.startsWith("-100")) {
			return raw.substring(4);
		}
		if (raw.startsWith("-")) {
			return raw.substring(1);
		}
		return raw;
	}
}
