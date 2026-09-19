package com.fava.ingest;

import java.util.List;

/**
 * Telegram-agnostic facts about an Inbox candidate message.
 */
public record InboxMessageFacts(
		long chatId,
		long messageId,
		Long threadId,
		String text,
		List<ExtractedUrl> urls,
		boolean forwarded,
		String forwardOriginText) {

	public InboxMessageFacts {
		urls = urls == null ? List.of() : List.copyOf(urls);
	}
}
