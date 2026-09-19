package com.fava.catalog;

/**
 * Outcome of Catalog Setup orchestration (theme parse → forum topics → persist).
 */
public sealed interface CatalogSetupResult {

	record Success(Catalog catalog) implements CatalogSetupResult {
	}

	/**
	 * Group is not forum-enabled; no topics created and nothing persisted.
	 */
	record NeedsForum() implements CatalogSetupResult {
	}

	record AlreadyConfigured(Catalog existing) implements CatalogSetupResult {
	}

	record EmptyThemeList() implements CatalogSetupResult {
	}
}
