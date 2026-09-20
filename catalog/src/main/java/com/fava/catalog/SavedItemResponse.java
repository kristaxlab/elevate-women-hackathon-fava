package com.fava.catalog;

/**
 * Developer API envelope for a Saved Item plus optional nested embedding.
 */
public record SavedItemResponse(SavedItem item, EmbeddingResponse embedding) {
}
