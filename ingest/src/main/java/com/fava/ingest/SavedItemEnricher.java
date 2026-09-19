package com.fava.ingest;

import java.util.Optional;

/**
 * Enrichment seam for Saved Items at Filing time. Must not throw for caller-facing failures.
 */
public interface SavedItemEnricher {

	SavedItemEnrichment enrich(Optional<String> url, String bodyText);
}
