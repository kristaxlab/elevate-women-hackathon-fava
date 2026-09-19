package com.fava.ingest;

/**
 * Outbound seam for Filing: copy Source Message into a Theme Topic thread and reply on Inbox.
 * Fake this in tests; no live Telegram HTTP.
 */
public interface FilingPort {

	void copyMessageToThread(long chatId, long fromMessageId, long messageThreadId);

	void replyToMessage(long chatId, long replyToMessageId, String text);
}
