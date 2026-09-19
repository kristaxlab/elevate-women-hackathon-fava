package com.fava.search;

import com.fava.catalog.EmbeddingDimensions;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemEmbeddingStore;
import com.fava.catalog.SavedItemIndexer;
import com.fava.classify.EmbeddingPort;

/**
 * Embeds a Saved Item (body + URL when present) and upserts into the Catalog embedding store.
 */
public final class EmbeddingSavedItemIndexer implements SavedItemIndexer {

	private final EmbeddingPort embeddings;
	private final SavedItemEmbeddingStore store;

	public EmbeddingSavedItemIndexer(EmbeddingPort embeddings, SavedItemEmbeddingStore store) {
		this.embeddings = embeddings;
		this.store = store;
	}

	@Override
	public void index(SavedItem item) {
		if (item.id() == null) {
			throw new IllegalArgumentException("Saved Item must be persisted before indexing");
		}
		String text = indexText(item);
		float[] vector = embeddings.embed(text);
		if (vector.length != EmbeddingDimensions.OPENAI_TEXT_EMBEDDING_3_SMALL) {
			throw new IllegalStateException(
					"embedding length " + vector.length + " != " + EmbeddingDimensions.OPENAI_TEXT_EMBEDDING_3_SMALL);
		}
		store.upsert(item.id(), item.chatId(), vector);
	}

	static String indexText(SavedItem item) {
		if (item.url().isPresent()) {
			return item.bodyText() + "\nURL: " + item.url().get();
		}
		return item.bodyText();
	}
}
