package com.fava.search;

/**
 * Public seam for answering Catalog Questions over a Catalog's Saved Items.
 */
public interface CatalogSearchPort {

	CatalogSearchResult answer(long chatId, String question);
}
