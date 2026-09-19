package com.fava.search;

import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemEmbeddingStore;
import com.fava.catalog.SavedItemHit;
import com.fava.catalog.SavedItemStore;
import com.fava.classify.ChatModelPort;
import com.fava.classify.EmbeddingPort;
import java.util.ArrayList;
import java.util.List;

/**
 * Answers Catalog Questions with RAG over that Catalog's Saved Items only.
 */
public final class CatalogSearchService implements CatalogSearchPort {

	public static final String NOTHING_FOUND_MESSAGE =
			"I couldn't find anything relevant in your Catalog for that question.";

	public static final int DEFAULT_TOP_K = 5;
	/** Cosine distance ceiling ({@code <=>}); keep only reasonably close hits. */
	public static final double DEFAULT_MAX_DISTANCE = 0.45;

	private static final String SYSTEM_PROMPT = """
			You answer questions using ONLY the Saved Items provided as context.
			Do not use outside knowledge. If the context is insufficient, say you could not find \
			anything relevant in the Catalog.
			You cannot open, fetch, or check external links from the question; use only Saved Items.
			Be concise. Do not invent URLs or facts that are not in the context.
			""";

	private final EmbeddingPort embeddingPort;
	private final SavedItemEmbeddingStore embeddingStore;
	private final SavedItemStore savedItemStore;
	private final ChatModelPort chatModel;
	private final int topK;
	private final double maxDistance;

	public CatalogSearchService(
			EmbeddingPort embeddingPort,
			SavedItemEmbeddingStore embeddingStore,
			SavedItemStore savedItemStore,
			ChatModelPort chatModel,
			int topK,
			double maxDistance) {
		this.embeddingPort = embeddingPort;
		this.embeddingStore = embeddingStore;
		this.savedItemStore = savedItemStore;
		this.chatModel = chatModel;
		this.topK = topK;
		this.maxDistance = maxDistance;
	}

	@Override
	public CatalogSearchResult answer(long chatId, String question) {
		if (question == null || question.isBlank()) {
			return new CatalogSearchResult.NothingFound(NOTHING_FOUND_MESSAGE);
		}
		float[] query = embeddingPort.embed(question.trim());
		List<SavedItemHit> hits = embeddingStore.findSimilar(chatId, query, topK, maxDistance);
		if (hits.isEmpty()) {
			return new CatalogSearchResult.NothingFound(NOTHING_FOUND_MESSAGE);
		}
		List<SavedItem> contextItems = new ArrayList<>();
		for (SavedItemHit hit : hits) {
			savedItemStore.findById(hit.savedItemId()).ifPresent(contextItems::add);
		}
		if (contextItems.isEmpty()) {
			return new CatalogSearchResult.NothingFound(NOTHING_FOUND_MESSAGE);
		}
		String userMessage = buildUserMessage(question.trim(), contextItems);
		String answerText = chatModel.complete(SYSTEM_PROMPT, userMessage);
		List<CatalogSearchResult.Citation> citations = contextItems.stream()
				.map(CatalogSearchService::toCitation)
				.toList();
		return new CatalogSearchResult.Answer(answerText == null ? "" : answerText.trim(), citations);
	}

	private static String buildUserMessage(String question, List<SavedItem> items) {
		StringBuilder sb = new StringBuilder();
		sb.append("Catalog Question: ").append(question).append("\n\nSaved Items:\n");
		for (int i = 0; i < items.size(); i++) {
			SavedItem item = items.get(i);
			sb.append(i + 1).append(". ").append(item.bodyText());
			item.url().ifPresent(url -> sb.append("\n   URL: ").append(url));
			sb.append('\n');
		}
		return sb.toString();
	}

	private static CatalogSearchResult.Citation toCitation(SavedItem item) {
		String snippet = snippet(item.bodyText());
		return new CatalogSearchResult.Citation(snippet, item.url());
	}

	private static String snippet(String body) {
		String trimmed = body.trim().replaceAll("\\s+", " ");
		if (trimmed.length() <= 120) {
			return trimmed;
		}
		return trimmed.substring(0, 117) + "...";
	}
}
