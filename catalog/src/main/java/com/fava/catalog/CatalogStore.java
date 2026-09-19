package com.fava.catalog;

import java.util.Optional;

/**
 * Persistence seam for Catalog Setup state: System Topics and Theme Topics ↔ thread ids.
 */
public interface CatalogStore {

	/**
	 * Persists a newly configured Catalog. Rejects a second Setup for the same chat.
	 */
	CatalogCreateResult create(Catalog catalog);

	Optional<Catalog> findByChatId(long chatId);

	boolean isConfigured(long chatId);
}
