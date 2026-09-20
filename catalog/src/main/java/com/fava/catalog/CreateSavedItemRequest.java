package com.fava.catalog;

import java.util.List;
import java.util.Optional;

/**
 * HTTP body for {@code POST /api/catalog/items}. Maps to {@link SavedItem} with harness defaults.
 */
public record CreateSavedItemRequest(
		Long id,
		long chatId,
		Optional<String> url,
		String bodyText,
		String themeName,
		Long sourceMessageId,
		Optional<String> userLibType,
		Optional<String> userLibItemId,
		Optional<SourceType> sourceType,
		Optional<String> title,
		Optional<String> recommendedBy,
		List<String> tags,
		Optional<String> searchText) {

	public CreateSavedItemRequest {
		url = url == null ? Optional.empty() : url;
		userLibType = userLibType == null ? Optional.empty() : userLibType;
		userLibItemId = userLibItemId == null ? Optional.empty() : userLibItemId;
		sourceType = sourceType == null ? Optional.empty() : sourceType;
		title = title == null ? Optional.empty() : title;
		recommendedBy = recommendedBy == null ? Optional.empty() : recommendedBy;
		searchText = searchText == null ? Optional.empty() : searchText;
		tags = tags == null ? List.of() : List.copyOf(tags);
	}

	SavedItem toSavedItem() {
		return new SavedItem(
				id,
				chatId,
				url,
				bodyText,
				themeName,
				sourceMessageId == null ? 0L : sourceMessageId,
				userLibType,
				userLibItemId,
				sourceType,
				title,
				recommendedBy,
				tags,
				searchText);
	}
}
