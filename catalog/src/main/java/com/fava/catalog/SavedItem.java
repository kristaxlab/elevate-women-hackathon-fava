package com.fava.catalog;

import java.util.Optional;

/**
 * One unit of content stored in a Catalog: optional URL plus Telegram-sourced text,
 * assigned to exactly one Theme Topic.
 */
public record SavedItem(
		Long id,
		long chatId,
		Optional<String> url,
		String bodyText,
		String themeName,
		long sourceMessageId) {

	public SavedItem {
		url = url == null ? Optional.empty() : url;
		if (bodyText == null || bodyText.isBlank()) {
			throw new IllegalArgumentException("bodyText must not be blank");
		}
		if (themeName == null || themeName.isBlank()) {
			throw new IllegalArgumentException("themeName must not be blank");
		}
	}
}
