package com.fava.catalog;

import com.fava.classify.EmbeddingPort;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Developer API write path: validate catalog/theme, URL-dedupe, embed, persist item + embedding.
 */
public final class CatalogItemCreator {

	private static final Logger log = LoggerFactory.getLogger(CatalogItemCreator.class);

	private final CatalogStore catalogs;
	private final SavedItemStore savedItems;
	private final SavedItemEmbeddingStore embeddings;
	private final EmbeddingModelRegistry models;
	private final EmbeddingPort embeddingPort;
	private final TransactionTemplate transactions;

	public CatalogItemCreator(
			CatalogStore catalogs,
			SavedItemStore savedItems,
			SavedItemEmbeddingStore embeddings,
			EmbeddingModelRegistry models,
			EmbeddingPort embeddingPort,
			TransactionTemplate transactions) {
		this.catalogs = catalogs;
		this.savedItems = savedItems;
		this.embeddings = embeddings;
		this.models = models;
		this.embeddingPort = embeddingPort;
		this.transactions = transactions;
	}

	public SavedItemCreateResult create(SavedItem request) {
		if (request == null) {
			throw new InvalidSavedItemCreateException("body is required");
		}
		if (request.id() != null) {
			throw new InvalidSavedItemCreateException("id must be null; server assigns ids");
		}

		Catalog catalog = catalogs
				.findByChatId(request.chatId())
				.orElseThrow(() -> new CatalogNotFoundException(request.chatId()));
		if (catalog.themes().stream().noneMatch(t -> t.name().equals(request.themeName()))) {
			throw new ThemeNotFoundException(request.chatId(), request.themeName());
		}

		if (request.url().isPresent()) {
			Optional<SavedItem> existing = savedItems.findByCatalogAndUrl(request.chatId(), request.url().get());
			if (existing.isPresent()) {
				SavedItem item = existing.get();
				Optional<StoredEmbedding> stored = embeddings.findBySavedItemId(item.id());
				log.info("Duplicate Saved Item URL in catalog {}: id={}", request.chatId(), item.id());
				return new SavedItemCreateResult.Duplicate(item, stored);
			}
		}

		EmbeddingModel active = models
				.findActive()
				.orElseThrow(() -> new EmbeddingProviderException("No active embedding model"));

		float[] vector;
		try {
			vector = embeddingPort.embed(indexText(request));
		}
		catch (RuntimeException e) {
			log.warn("Embedding provider failed for catalog {}: {}", request.chatId(), e.toString());
			throw new EmbeddingProviderException("Embedding provider failed", e);
		}
		if (vector == null || vector.length != active.dimensions()) {
			throw new EmbeddingProviderException(
					"embedding length " + (vector == null ? 0 : vector.length) + " != " + active.dimensions());
		}

		StoredEmbedding embeddingInfo = new StoredEmbedding(active.modelId(), active.dimensions(), vector);
		float[] vectorToStore = vector;

		SavedItem toSave = new SavedItem(
				null,
				request.chatId(),
				request.url(),
				request.bodyText(),
				request.themeName(),
				request.sourceMessageId(),
				request.userLibType(),
				request.userLibItemId(),
				request.sourceType(),
				request.title(),
				request.recommendedBy(),
				request.tags(),
				request.searchText());
		SavedItem saved = transactions.execute(status -> {
			SavedItem persisted = savedItems.save(toSave);
			embeddings.upsert(persisted.id(), persisted.chatId(), vectorToStore, active.id());
			return persisted;
		});
		if (saved == null) {
			throw new IllegalStateException("transaction returned null Saved Item");
		}

		log.info("Created Saved Item {} in catalog {}", saved.id(), saved.chatId());
		return new SavedItemCreateResult.Created(saved, embeddingInfo);
	}

	static String indexText(SavedItem item) {
		if (item.searchText().isPresent() && !item.searchText().get().isBlank()) {
			return item.searchText().get().trim();
		}
		return item.bodyText();
	}
}
