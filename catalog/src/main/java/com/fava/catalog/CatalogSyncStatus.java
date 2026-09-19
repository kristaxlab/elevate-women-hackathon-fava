package com.fava.catalog;

/**
 * Catalog-wide embedding sync progress for the active embedding model.
 */
public enum CatalogSyncStatus {
	PENDING,
	IN_PROGRESS,
	SUCCEEDED,
	FAILED
}
