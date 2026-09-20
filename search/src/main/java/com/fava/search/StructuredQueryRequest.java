package com.fava.search;

/**
 * HTTP request body for natural-language to structured query parsing.
 */
public record StructuredQueryRequest(String query) {

	public StructuredQueryRequest {
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("query must not be blank");
		}
		query = query.trim();
	}
}
