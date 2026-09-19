package com.fava.ingest;

import com.fava.catalog.SourceType;
import java.util.List;
import java.util.Optional;

/**
 * Structured metadata extracted for a Saved Item at save time (LLM + heuristics).
 */
public record SavedItemEnrichment(
		Optional<SourceType> sourceType,
		Optional<String> title,
		Optional<String> recommendedBy,
		List<String> tags,
		Optional<String> searchText) {

	public static SavedItemEnrichment empty() {
		return new SavedItemEnrichment(
				Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.empty());
	}

	public SavedItemEnrichment {
		sourceType = sourceType == null ? Optional.empty() : sourceType;
		title = title == null ? Optional.empty() : title;
		recommendedBy = recommendedBy == null ? Optional.empty() : recommendedBy;
		searchText = searchText == null ? Optional.empty() : searchText;
		tags = tags == null ? List.of() : List.copyOf(tags);
	}
}
