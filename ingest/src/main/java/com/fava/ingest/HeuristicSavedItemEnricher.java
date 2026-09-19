package com.fava.ingest;

import java.util.Optional;

/**
 * Enrichment without a chat model: URL heuristics for {@code source_type} only.
 */
public final class HeuristicSavedItemEnricher implements SavedItemEnricher {

	@Override
	public SavedItemEnrichment enrich(Optional<String> url, String bodyText) {
		return new SavedItemEnrichment(
				UrlSourceTypeHeuristics.fromUrl(url),
				Optional.empty(),
				Optional.empty(),
				java.util.List.of(),
				Optional.empty());
	}
}
