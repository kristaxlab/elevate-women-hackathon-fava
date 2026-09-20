package com.fava.catalog;

import java.util.Optional;

/**
 * One Catalog list row: Saved Item plus optional stored embedding.
 */
public record ListedSavedItem(SavedItem item, Optional<StoredEmbedding> embedding) {
}
