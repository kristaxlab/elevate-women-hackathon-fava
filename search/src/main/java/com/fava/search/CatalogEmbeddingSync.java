package com.fava.search;

import com.fava.catalog.CatalogSchema;
import com.fava.catalog.CatalogSyncStatus;
import com.fava.catalog.EmbeddingModel;
import com.fava.catalog.EmbeddingModelRegistry;
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
	private final int configuredDimensions;

	public CatalogEmbeddingSync(
			DataSource dataSource,
			EmbeddingModelRegistry registry,
			SavedItemEmbeddingStore embeddings,
			EmbeddingPort embeddingPort,
			int configuredDimensions) {
		this.dataSource = dataSource;
		this.registry = registry;
		this.embeddings = embeddings;
		this.embeddingPort = embeddingPort;
		this.configuredDimensions = configuredDimensions;
	}

	/**
	 * Aligns registry and embeddings with {@code configuredModelId}/{@code configuredDimensions}.
	 * Throws if sync fails after marking the active model {@link CatalogSyncStatus#FAILED}.
	 */
	public void ensureSynced(String configuredModelId, int configuredDimensions) {
		if (configuredDimensions != this.configuredDimensions) {
			throw new IllegalArgumentException(
					"configuredDimensions " + configuredDimensions + " != store dimensions " + this.configuredDimensions);
		}
		Optional<EmbeddingModel> activeOpt = registry.findActive();
		if (activeOpt.isEmpty()) {
			EmbeddingModel model = registry.activate(configuredModelId, configuredDimensions, CatalogSyncStatus.IN_PROGRESS);
			reindexAllNeeding(model);
			return;
		}

		EmbeddingModel active = activeOpt.get();
		boolean changed = !active.modelId().equals(configuredModelId) || active.dimensions() != configuredDimensions;
		if (changed) {
			if (active.dimensions() != configuredDimensions) {
				CatalogSchema.recreateEmbeddingsTable(dataSource, configuredDimensions);
			} else {
				embeddings.deleteAll();
			}
			EmbeddingModel model =
					registry.activate(configuredModelId, configuredDimensions, CatalogSyncStatus.IN_PROGRESS);
			reindexAllNeeding(model);
			return;
		}

		if (active.catalogSyncStatus() == CatalogSyncStatus.SUCCEEDED) {
			return;
		}

		registry.updateSyncStatus(active.id(), CatalogSyncStatus.IN_PROGRESS);
		reindexAllNeeding(active);
	}

	private void reindexAllNeeding(EmbeddingModel model) {
		EmbeddingSavedItemIndexer indexer =
				new EmbeddingSavedItemIndexer(embeddingPort, embeddings, registry, configuredDimensions);
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
