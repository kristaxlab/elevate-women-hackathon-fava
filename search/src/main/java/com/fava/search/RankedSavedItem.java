package com.fava.search;

import com.fava.catalog.SavedItem;

/**
 * One ranked hit from structured Catalog search (no embedding payload).
 *
 * @param distance cosine distance; lower is closer
 */
public record RankedSavedItem(SavedItem item, double distance) {
}
