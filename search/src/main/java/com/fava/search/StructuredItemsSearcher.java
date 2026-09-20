package com.fava.search;

import com.fava.catalog.CatalogNotFoundException;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.EmbeddingProviderException;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemEmbeddingStore;
import com.fava.catalog.SavedItemFilters;
import com.fava.catalog.SavedItemHit;
import com.fava.catalog.SavedItemStore;
import com.fava.classify.EmbeddingPort;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Developer API structured search: filter → embed query → similarity → hydrate with distances.
 * Does not use {@link CatalogSearchPort#answer} (no NL parse, intro, or Telegram formatting).
 */
public final class StructuredItemsSearcher {

	private static final Logger log = LoggerFactory.getLogger(StructuredItemsSearcher.class);

	private final CatalogStore catalogs;
	private final SavedItemStore savedItems;
	private final SavedItemEmbeddingStore embeddings;
	private final EmbeddingPort embeddingPort;

	public StructuredItemsSearcher(
			CatalogStore catalogs,
			SavedItemStore savedItems,
			SavedItemEmbeddingStore embeddings,
			EmbeddingPort embeddingPort) {
		this.catalogs = catalogs;
		this.savedItems = savedItems;
		this.embeddings = embeddings;
		this.embeddingPort = embeddingPort;
	}

	public List<RankedSavedItem> search(long chatId, StructuredQuery structured, double maxDistance) {
		if (structured == null) {
			throw new IllegalArgumentException("structured query is required");
		}
		catalogs.findByChatId(chatId).orElseThrow(() -> new CatalogNotFoundException(chatId));

		List<RankedSavedItem> hits = retrieve(chatId, structured, maxDistance);
		if (hits.isEmpty()) {
			throw new NoSearchHitsException(chatId);
		}
		return hits;
	}

	List<RankedSavedItem> retrieve(long chatId, StructuredQuery structured, double maxDistance) {
		SavedItemFilters filters = toFilters(structured.filters());
		List<Long> candidateIds = null;
		if (!filters.isEmpty()) {
			List<SavedItem> filtered = savedItems.findByCatalogFilters(chatId, filters);
			if (filtered.isEmpty()) {
				return List.of();
			}
			candidateIds = filtered.stream().map(SavedItem::id).toList();
		}

		float[] queryVector;
		try {
			queryVector = embeddingPort.embed(structured.query());
		}
		catch (RuntimeException e) {
			log.warn("Embedding provider failed for catalog search {}: {}", chatId, e.toString());
			throw new EmbeddingProviderException("Embedding provider failed", e);
		}

		List<SavedItemHit> similar = candidateIds == null
				? embeddings.findSimilar(chatId, queryVector, structured.limit(), maxDistance)
				: embeddings.findSimilarAmong(
						chatId, queryVector, structured.limit(), maxDistance, candidateIds);
		if (similar.isEmpty()) {
			return List.of();
		}

		List<RankedSavedItem> ranked = new ArrayList<>();
		for (SavedItemHit hit : similar) {
			savedItems.findById(hit.savedItemId())
					.ifPresent(item -> ranked.add(new RankedSavedItem(item, hit.distance())));
		}
		return ranked;
	}

	private static SavedItemFilters toFilters(StructuredQuery.Filters filters) {
		return new SavedItemFilters(
				filters.sourceType(),
				filters.since(),
				filters.recommendedBy(),
				filters.tags(),
				filters.themeName());
	}
}
