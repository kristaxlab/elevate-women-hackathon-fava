package com.fava.intent;

import com.fava.ingest.ExtractedUrl;
import com.fava.ingest.InboxMessageFacts;
import java.util.List;

/**
 * Telegram-free summary of a Catalog message for the Intent Router.
 */
public record RoutedRequest(
		long chatId,
		long messageId,
		Long threadId,
		MessageLocus locus,
		Long userId,
		String text,
		List<ExtractedUrl> urls,
		boolean forwarded,
		String forwardOriginText) {

	public RoutedRequest {
		urls = urls == null ? List.of() : List.copyOf(urls);
	}

	public InboxMessageFacts toInboxFacts() {
		return new InboxMessageFacts(
				chatId, messageId, threadId, text, urls, forwarded, forwardOriginText);
	}
}
