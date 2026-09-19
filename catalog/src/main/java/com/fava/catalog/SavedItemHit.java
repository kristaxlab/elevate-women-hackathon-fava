package com.fava.catalog;

/**
 * One similarity hit from the Catalog's embedding index.
 *
 * @param distance cosine distance ({@code <=>}); lower is closer
 */
public record SavedItemHit(long savedItemId, double distance) {
}
