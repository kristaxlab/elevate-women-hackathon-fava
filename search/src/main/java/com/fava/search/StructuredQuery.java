package com.fava.search;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Structured retrieval request parsed from a Catalog Question.
 *
 * @param query English retrieval string for semantic rank
 * @param limit result count (1–10; callers clamp)
 * @param filters explicit facets only; omit unset fields
 */
public record StructuredQuery(String query, int limit, Filters filters) {

	public static final int DEFAULT_LIMIT = 3;
	public static final int MAX_LIMIT = 10;

	public StructuredQuery {
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("query must not be blank");
		}
		query = query.trim();
		limit = clampLimit(limit);
		filters = filters == null ? Filters.NONE : filters;
	}

	public static int clampLimit(int requested) {
		if (requested < 1) {
			return DEFAULT_LIMIT;
		}
		return Math.min(requested, MAX_LIMIT);
	}

	/**
	 * Explicit Saved Item facets. Tags are AND. Empty means no filter of that kind.
	 */
	public record Filters(
			Optional<String> sourceType,
			Optional<Instant> since,
			Optional<String> recommendedBy,
			List<String> tags,
			Optional<String> themeName) {

		public static final Filters NONE = new Filters(
				Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.empty());

		public Filters {
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
}
