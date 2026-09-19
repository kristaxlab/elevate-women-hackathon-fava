package com.fava.catalog;

import java.util.List;

/**
 * Persistence seam for Saved Item embeddings (pgvector), scoped by Catalog {@code chat_id}.
 */
public interface SavedItemEmbeddingStore {

	void upsert(long savedItemId, long chatId, float[] embedding);

	/**
	 * Nearest Saved Items in this Catalog by cosine distance, excluding hits with
	 * {@code distance > maxDistance}.
	 */
	List<SavedItemHit> findSimilar(long chatId, float[] queryEmbedding, int limit, double maxDistance);
}
