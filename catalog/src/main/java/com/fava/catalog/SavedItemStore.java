package com.fava.catalog;

import java.util.Optional;

/**
 * Persistence seam for Saved Items within a Catalog.
 */
public interface SavedItemStore {

	SavedItem save(SavedItem item);

	Optional<SavedItem> findByCatalogAndUrl(long chatId, String url);

	Optional<SavedItem> findById(long id);
}
