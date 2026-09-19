package com.fava.catalog;

import java.util.List;
import java.util.Optional;

/**
 * Persistence seam for Saved Items within a Catalog.
 */
public interface SavedItemStore {

	SavedItem save(SavedItem item);

	Optional<SavedItem> findByCatalogAndUrl(long chatId, String url);

	Optional<SavedItem> findById(long id);

	/**
	 * Keyword search over body text (and URL when present) within one Catalog.
	 * Used for offline / no-API-key degraded Smart Search.
	 */
	List<SavedItem> findByCatalogKeyword(long chatId, String keyword, int limit);
}
