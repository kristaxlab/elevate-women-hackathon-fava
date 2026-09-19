package com.fava.catalog;

import java.util.Locale;

/**
 * Kind of content a Saved Item refers to (enrichment / filter facet).
 */
public enum SourceType {
	INSTAGRAM,
	YOUTUBE,
	LINKEDIN,
	BOOK,
	MOVIE,
	GAME,
	RECIPE,
	ARTICLE,
	NOTE,
	OTHER,
	UNKNOWN;

	/** Wire / DB value (lowercase enum name). */
	public String wireValue() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static SourceType fromWire(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("source_type must not be blank");
		}
		return SourceType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
	}
}
