package com.fava.search;

import java.util.List;
import java.util.Optional;

/**
 * Outcome of answering a Catalog Question.
 */
public sealed interface CatalogSearchResult {

	record Answer(String text, List<Citation> citations) implements CatalogSearchResult {
		public Answer {
			citations = List.copyOf(citations);
		}
	}

	record NothingFound(String message) implements CatalogSearchResult {
	}

	/**
	 * Pointer back to a Saved Item for a Catalog Answer.
	 */
	record Citation(String snippet, Optional<String> url) {
		public Citation {
			url = url == null ? Optional.empty() : url;
		}
	}
}
