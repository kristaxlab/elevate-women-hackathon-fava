package com.fava.search;

import com.fava.catalog.CatalogSchema;
import com.fava.catalog.CatalogSyncStatus;
import com.fava.catalog.EmbeddingModel;
import com.fava.catalog.EmbeddingModelRegistry;
import com.fava.catalog.EmbeddingSpace;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemEmbeddingStore;
import com.fava.classify.EmbeddingPort;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Ensures the Catalog embedding space matches the configured model/dimensions on startup:
 * seed registry, wipe on change, re-embed Saved Items, resume after {@link CatalogSyncStatus#FAILED}.
 */
public final class CatalogEmbeddingSync {

	private final DataSource dataSource;
	private final EmbeddingModelRegistry registry;
	private final SavedItemEmbeddingStore embeddings;
	private final EmbeddingPort embeddingPort;
	private final EmbeddingSpace storeSpace;

	public CatalogEmbeddingSync(
			DataSource dataSource,
			EmbeddingModelRegistry registry,
			SavedItemEmbeddingStore embeddings,
			EmbeddingPort embeddingPort,
			EmbeddingSpace storeSpace) {
		this.dataSource = dataSource;
		this.registry = registry;
		this.embeddings = embeddings;
		this.embeddingPort = embeddingPort;
		this.storeSpace = storeSpace;
	}

	/**
	 * Aligns registry and embeddings with {@code configured}.
	 * Throws if sync fails after marking the active model {@link CatalogSyncStatus#FAILED}.
	 */
	public void ensureSynced(EmbeddingSpace configured) {
		if (!configured.equals(storeSpace)) {
			throw new IllegalArgumentException(
					"configured space " + configured + " != store space " + storeSpace);
		}
		Optional<EmbeddingModel> activeOpt = registry.findActive();
		if (activeOpt.isEmpty()) {
			EmbeddingModel model = registry.activate(configured, CatalogSyncStatus.PENDING);
			reindexAllNeeding(model);
			return;
		}

		EmbeddingModel active = activeOpt.get();
		if (configured.differsFrom(active)) {
			if (active.dimensions() != configured.dimensions()) {
				CatalogSchema.recreateEmbeddingsTable(dataSource, configured.dimensions());
			} else {
				embeddings.deleteAll();
			}
			EmbeddingModel model = registry.activate(configured, CatalogSyncStatus.PENDING);
			reindexAllNeeding(model);
			return;
		}

		if (active.catalogSyncStatus() == CatalogSyncStatus.SUCCEEDED) {
			return;
		}

		reindexAllNeeding(active);
	}

	private void reindexAllNeeding(EmbeddingModel model) {
		registry.updateSyncStatus(model.id(), CatalogSyncStatus.IN_PROGRESS);
		EmbeddingSavedItemIndexer indexer =
				new EmbeddingSavedItemIndexer(embeddingPort, embeddings, registry, storeSpace.dimensions());
		try {
			for (SavedItem item : embeddings.findNeedingEmbedding(model.id())) {
				indexer.index(item);
			}
			registry.updateSyncStatus(model.id(), CatalogSyncStatus.SUCCEEDED);
		}
		catch (RuntimeException ex) {
			registry.updateSyncStatus(model.id(), CatalogSyncStatus.FAILED);
			throw new IllegalStateException("Catalog embedding sync failed", ex);
		}
	}
}
