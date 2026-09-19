package com.fava.catalog;

/**
 * Outcome of attempting to persist a Catalog from Setup.
 */
public sealed interface CatalogCreateResult {

	record Created(Catalog catalog) implements CatalogCreateResult {
	}

	/**
	 * Setup already completed for this chat; v1 refuses a second configuration.
	 */
	record AlreadyConfigured(Catalog existing) implements CatalogCreateResult {
	}
}
