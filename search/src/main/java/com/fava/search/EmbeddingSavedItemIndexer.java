package com.fava.search;

import com.fava.catalog.EmbeddingModel;
import com.fava.catalog.EmbeddingModelRegistry;
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
	private final EmbeddingModelRegistry registry;
	private final int expectedDimensions;

	public EmbeddingSavedItemIndexer(
			EmbeddingPort embeddings,
			SavedItemEmbeddingStore store,
			EmbeddingModelRegistry registry,
			int expectedDimensions) {
		this.embeddings = embeddings;
		this.store = store;
		this.registry = registry;
		this.expectedDimensions = expectedDimensions;
	}

	@Override
	public void index(SavedItem item) {
		if (item.id() == null) {
			throw new IllegalArgumentException("Saved Item must be persisted before indexing");
		}
		EmbeddingModel active = registry
				.findActive()
				.orElseThrow(() -> new IllegalStateException("No active embedding model; run Catalog embedding sync first"));
		String text = indexText(item);
		float[] vector = embeddings.embed(text);
		if (vector.length != expectedDimensions) {
			throw new IllegalStateException(
					"embedding length " + vector.length + " != " + expectedDimensions
							+ " (set fava.openrouter.embedding-dimensions / FAVA_OPENROUTER_EMBEDDING_DIMENSIONS to match the model)");
		}
		store.upsert(item.id(), item.chatId(), vector, active.id());
	}

	static String indexText(SavedItem item) {
		if (item.url().isPresent()) {
			return item.bodyText() + "\nURL: " + item.url().get();
		}
		return item.bodyText();
	}
}
