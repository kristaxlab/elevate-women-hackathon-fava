package com.fava.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Developer API list path: all Saved Items in a Catalog with nested embeddings (or null).
 */
public final class CatalogItemLister {

	private final CatalogStore catalogs;
	private final SavedItemStore savedItems;
	private final SavedItemEmbeddingStore embeddings;

	public CatalogItemLister(CatalogStore catalogs, SavedItemStore savedItems, SavedItemEmbeddingStore embeddings) {
		this.catalogs = catalogs;
		this.savedItems = savedItems;
		this.embeddings = embeddings;
	}

	public List<ListedSavedItem> list(long chatId) {
		catalogs.findByChatId(chatId).orElseThrow(() -> new CatalogNotFoundException(chatId));
		List<SavedItem> items = savedItems.findByCatalogFilters(chatId, SavedItemFilters.NONE);
		List<ListedSavedItem> listed = new ArrayList<>(items.size());
		for (SavedItem item : items) {
			Optional<StoredEmbedding> embedding = embeddings.findBySavedItemId(item.id());
			listed.add(new ListedSavedItem(item, embedding));
		}
		return listed;
	}
}
