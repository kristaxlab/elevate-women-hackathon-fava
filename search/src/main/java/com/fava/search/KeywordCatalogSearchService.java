package com.fava.search;

import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemStore;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline / no-API-key Smart Search: keyword {@code ILIKE} over Saved Items in this Catalog only.
 * Does not call an LLM and never invents external content.
 */
public final class KeywordCatalogSearchService implements CatalogSearchPort {

	public static final String DEGRADED_INTRO =
			"AI is not configured, so I matched keywords in your Catalog only.";

	private final SavedItemStore savedItems;
	private final int limit;

	public KeywordCatalogSearchService(SavedItemStore savedItems, int limit) {
		this.savedItems = savedItems;
		this.limit = limit;
	}

	@Override
	public CatalogSearchResult answer(long chatId, String question) {
		if (question == null || question.isBlank()) {
			return new CatalogSearchResult.NothingFound(CatalogSearchService.NOTHING_FOUND_MESSAGE);
		}
		List<SavedItem> hits = findKeywordHits(chatId, question.trim());
		if (hits.isEmpty()) {
			return new CatalogSearchResult.NothingFound(CatalogSearchService.NOTHING_FOUND_MESSAGE);
		}
		List<CatalogSearchResult.Citation> citations = hits.stream()
				.map(item -> new CatalogSearchResult.Citation(snippet(item.bodyText()), item.url()))
				.toList();
		return new CatalogSearchResult.Answer(DEGRADED_INTRO, citations);
	}

	private List<SavedItem> findKeywordHits(long chatId, String question) {
		Set<Long> seen = new LinkedHashSet<>();
		List<SavedItem> ordered = new java.util.ArrayList<>();
		for (String token : significantTokens(question)) {
			for (SavedItem item : savedItems.findByCatalogKeyword(chatId, token, limit)) {
				if (seen.add(item.id())) {
					ordered.add(item);
					if (ordered.size() >= limit) {
						return ordered;
					}
				}
			}
		}
		if (ordered.isEmpty()) {
			return savedItems.findByCatalogKeyword(chatId, question, limit);
		}
		return ordered;
	}

	static List<String> significantTokens(String question) {
		return Arrays.stream(question.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
				.filter(t -> t.length() >= 3)
				.distinct()
				.limit(8)
				.toList();
	}

	private static String snippet(String body) {
		String trimmed = body.trim().replaceAll("\\s+", " ");
		if (trimmed.length() <= 120) {
			return trimmed;
		}
		return trimmed.substring(0, 117) + "...";
	}
}
