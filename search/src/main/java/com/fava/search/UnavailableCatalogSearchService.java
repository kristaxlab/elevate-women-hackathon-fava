package com.fava.search;

/**
 * Smart Search when OpenRouter / LLM is not configured: clear unavailable message,
 * no keyword or {@code ILIKE} degraded fallback.
 */
public final class UnavailableCatalogSearchService implements CatalogSearchPort {

	public static final String AI_UNAVAILABLE_MESSAGE =
			"Smart Search isn't available because AI isn't configured. "
					+ "Ask a Catalog Owner to set an OpenRouter API key.";

	@Override
	public CatalogSearchResult answer(long chatId, String question) {
		return new CatalogSearchResult.Unavailable(AI_UNAVAILABLE_MESSAGE);
	}
}
