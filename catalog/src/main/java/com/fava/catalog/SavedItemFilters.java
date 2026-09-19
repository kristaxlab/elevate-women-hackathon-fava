package com.fava.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Explicit Saved Item facets for Smart Search filtering. Tags are AND.
 * Omit unset fields; an empty filter set matches the whole Catalog.
 */
public record SavedItemFilters(
		Optional<String> sourceType,
		Optional<Instant> since,
		Optional<String> recommendedBy,
		List<String> tags,
		Optional<String> themeName) {

	public static final SavedItemFilters NONE = new SavedItemFilters(
			Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.empty());

	public SavedItemFilters {
		sourceType = sourceType == null ? Optional.empty() : sourceType;
		since = since == null ? Optional.empty() : since;
		recommendedBy = recommendedBy == null ? Optional.empty() : recommendedBy;
		themeName = themeName == null ? Optional.empty() : themeName;
		tags = tags == null ? List.of() : List.copyOf(tags);
	}

	public boolean isEmpty() {
		return sourceType.isEmpty()
				&& since.isEmpty()
				&& recommendedBy.isEmpty()
				&& tags.isEmpty()
				&& themeName.isEmpty();
	}
}
