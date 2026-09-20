package com.fava.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence seam for Saved Item embeddings (pgvector), scoped by Catalog {@code chat_id}.
 */
public interface SavedItemEmbeddingStore {

	void upsert(long savedItemId, long chatId, float[] embedding, long embeddingModelId);

	/**
	 * Embedding row for a Saved Item, joined with registry model id and dimensions.
	 * Empty when no embedding row exists.
	 */
	Optional<StoredEmbedding> findBySavedItemId(long savedItemId);

	/**
	 * Nearest Saved Items in this Catalog by cosine distance, excluding hits with
	 * {@code distance > maxDistance}.
	 */
	List<SavedItemHit> findSimilar(long chatId, float[] queryEmbedding, int limit, double maxDistance);

	/**
	 * Like {@link #findSimilar}, but only among {@code candidateIds}. Empty candidates → empty list.
	 */
	List<SavedItemHit> findSimilarAmong(
			long chatId,
			float[] queryEmbedding,
			int limit,
			double maxDistance,
			Collection<Long> candidateIds);

	/** Deletes every embedding row (Catalog contents untouched). */
	void deleteAll();

	/**
	 * Saved Items with no embedding row, or whose {@code embedding_model_id} is not
	 * {@code activeEmbeddingModelId}.
	 */
	List<SavedItem> findNeedingEmbedding(long activeEmbeddingModelId);
}
