package com.fava.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence seam for Saved Items within a Catalog.
 */
public interface SavedItemStore {

	SavedItem save(SavedItem item);

	/**
	 * Sets {@code user_lib_type} / {@code user_lib_item_id} on an existing Saved Item after Filing copy.
	 *
	 * @return the updated item
	 */
	SavedItem updateUserLib(long id, String userLibType, String userLibItemId);

	Optional<SavedItem> findByCatalogAndUrl(long chatId, String url);

	Optional<SavedItem> findById(long id);

	/**
	 * Sets {@code created_at} for seed / test probes (e.g. Smart Search {@code since} filters).
	 */
	void updateCreatedAt(long id, Instant createdAt);

	/**
	 * Explicit-facet filter within one Catalog. Tags are AND.
	 * When {@code filters} is empty, returns every Saved Item in the Catalog.
	 */
	List<SavedItem> findByCatalogFilters(long chatId, SavedItemFilters filters);
}
