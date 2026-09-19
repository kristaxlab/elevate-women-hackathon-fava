package com.fava.ingest;

import java.util.List;
import java.util.Optional;

/**
 * Outbound seam for Filing: copy Source Message into a Theme Topic thread, reply on Inbox,
 * ask for a Theme Topic pick via callback buttons, and answer callback queries.
 * Fake this in tests; no live Telegram HTTP.
 */
public interface FilingPort {

	/**
	 * Copies the Source Message into a Theme Topic thread.
	 *
	 * @return the Theme Topic copy's message id when known; empty if copy failed or id could not be read
	 */
	Optional<Long> copyMessageToThread(long chatId, long fromMessageId, long messageThreadId);

	void replyToMessage(long chatId, long replyToMessageId, String text);

	void replyWithCallbackButtons(
			long chatId, long replyToMessageId, String text, List<FilingCallbackButton> buttons);

	void answerCallbackQuery(String callbackQueryId);
}
