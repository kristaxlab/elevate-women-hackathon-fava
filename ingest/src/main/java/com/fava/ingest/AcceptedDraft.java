package com.fava.ingest;

import java.util.Optional;

/**
 * Accepted Inbox draft ready for Filing (Telegram-provided text/URL only).
 */
public record AcceptedDraft(
		long chatId,
		long sourceMessageId,
		Long threadId,
		Optional<String> url,
		String bodyText) {

	public AcceptedDraft {
		url = url == null ? Optional.empty() : url;
		if (bodyText == null) {
			throw new IllegalArgumentException("bodyText must not be null");
		}
	}
}
