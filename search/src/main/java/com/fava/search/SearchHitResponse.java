package com.fava.search;

import com.fava.catalog.SavedItem;

/**
 * HTTP envelope for one structured-search hit (no embedding payload).
 */
public record SearchHitResponse(SavedItem item, double distance) {
}
