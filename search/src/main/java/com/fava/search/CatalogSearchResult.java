package com.fava.search;

import java.util.List;
import java.util.Optional;

/**
 * Outcome of answering a Catalog Question (ranked Saved Item list + grounded intro).
 */
public sealed interface CatalogSearchResult {

	/**
	 * Ranked list with a short grounded intro. Intro must only reference returned items.
	 */
	record Answer(String intro, List<Citation> items) implements CatalogSearchResult {
		public Answer {
			intro = intro == null ? "" : intro;
			items = List.copyOf(items);
		}
	}

	record NothingFound(String message) implements CatalogSearchResult {
	}

	/** Smart Search cannot run because AI / OpenRouter is not configured. */
	record Unavailable(String message) implements CatalogSearchResult {
	}

	/**
	 * A list entry in a Catalog Answer pointing at a Saved Item.
	 *
	 * @param title display title (enriched title, else body snippet)
	 * @param sourceType wire source_type when present
	 * @param url original URL when present
	 * @param themeTopicLink Theme Topic deep link when {@code user_lib_*} is present
	 */
	record Citation(
			String title,
			Optional<String> sourceType,
			Optional<String> url,
			Optional<String> themeTopicLink) {
		public Citation {
			sourceType = sourceType == null ? Optional.empty() : sourceType;
			url = url == null ? Optional.empty() : url;
			themeTopicLink = themeTopicLink == null ? Optional.empty() : themeTopicLink;
		}
	}
}
