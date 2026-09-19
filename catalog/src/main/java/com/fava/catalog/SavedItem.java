package com.fava.catalog;

import java.util.List;
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
		long sourceMessageId,
		Optional<String> userLibType,
		Optional<String> userLibItemId,
		Optional<SourceType> sourceType,
		Optional<String> title,
		Optional<String> recommendedBy,
		List<String> tags,
		Optional<String> searchText) {

	public static final String USER_LIB_TYPE_TELEGRAM = "telegram";

	/**
	 * Minimal Saved Item before user_lib / enrichment fields are set (expand-phase callers).
	 */
	public static SavedItem of(
			Long id,
			long chatId,
			Optional<String> url,
			String bodyText,
			String themeName,
			long sourceMessageId) {
		return new SavedItem(
				id,
				chatId,
				url,
				bodyText,
				themeName,
				sourceMessageId,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				List.of(),
				Optional.empty());
	}

	public SavedItem {
		url = url == null ? Optional.empty() : url;
		userLibType = userLibType == null ? Optional.empty() : userLibType;
		userLibItemId = userLibItemId == null ? Optional.empty() : userLibItemId;
		sourceType = sourceType == null ? Optional.empty() : sourceType;
		title = title == null ? Optional.empty() : title;
		recommendedBy = recommendedBy == null ? Optional.empty() : recommendedBy;
		searchText = searchText == null ? Optional.empty() : searchText;
		tags = tags == null ? List.of() : List.copyOf(tags);
		if (bodyText == null || bodyText.isBlank()) {
			throw new IllegalArgumentException("bodyText must not be blank");
		}
		if (themeName == null || themeName.isBlank()) {
			throw new IllegalArgumentException("themeName must not be blank");
		}
	}
}
