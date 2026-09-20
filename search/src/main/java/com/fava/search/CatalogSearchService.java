package com.fava.search;

import com.fava.catalog.SavedItem;
import com.fava.catalog.SourceType;
import com.fava.classify.ChatModelPort;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Smart Search: structured query → explicit filters → semantic rank → grounded list intro.
 */
public final class CatalogSearchService implements CatalogSearchPort {

	public static final String NOTHING_FOUND_MESSAGE =
			"I couldn't find anything matching that in your Catalog.";

	public static final String DEFAULT_INTRO_TEMPLATE = "Here are matching saves from your Catalog.";

	/** Cosine distance ceiling ({@code <=>}); keep only reasonably close hits. */
	public static final double DEFAULT_MAX_DISTANCE = 0.45;

	private static final String INTRO_SYSTEM_PROMPT = """
			Write a short intro (1–2 sentences) for a Catalog search result list.
			You may ONLY mention the titles, source types, and count of the items provided.
			Do not invent Saved Items, URLs, or facts. Do not use outside knowledge.
			Be concise.
			""";

	private final StructuredQueryParser queryParser;
	private final StructuredItemsSearcher itemsSearcher;
	private final ChatModelPort chatModel;
	private final double maxDistance;

	public CatalogSearchService(
			StructuredQueryParser queryParser,
			StructuredItemsSearcher itemsSearcher,
			ChatModelPort chatModel,
			double maxDistance) {
		this.queryParser = queryParser;
		this.itemsSearcher = itemsSearcher;
		this.chatModel = chatModel;
		this.maxDistance = maxDistance;
	}

	@Override
	public CatalogSearchResult answer(long chatId, String question) {
		if (question == null || question.isBlank()) {
			return new CatalogSearchResult.NothingFound(NOTHING_FOUND_MESSAGE);
		}
		StructuredQuery structured = queryParser.parse(question.trim());
		List<SavedItem> ranked = itemsSearcher.retrieve(chatId, structured, maxDistance).stream()
				.map(RankedSavedItem::item)
				.toList();
		if (ranked.isEmpty()) {
			return new CatalogSearchResult.NothingFound(NOTHING_FOUND_MESSAGE);
		}
		List<CatalogSearchResult.Citation> items = ranked.stream()
				.map(item -> toCitation(chatId, item))
				.toList();
		String intro = chatModel.complete(INTRO_SYSTEM_PROMPT, buildIntroUserMessage(items));
		if (intro == null || intro.isBlank()) {
			intro = DEFAULT_INTRO_TEMPLATE;
		}
		return new CatalogSearchResult.Answer(intro.trim(), items);
	}

	private static CatalogSearchResult.Citation toCitation(long chatId, SavedItem item) {
		return new CatalogSearchResult.Citation(
				displayTitle(item),
				item.sourceType().map(SourceType::wireValue),
				item.url(),
				themeTopicLink(chatId, item));
	}

	private static Optional<String> themeTopicLink(long chatId, SavedItem item) {
		Optional<String> itemId = item.userLibItemId().filter(id -> !id.isBlank());
		if (itemId.isEmpty()) {
			return Optional.empty();
		}
		boolean telegramPointer = item.userLibType().isEmpty()
				|| SavedItem.USER_LIB_TYPE_TELEGRAM.equals(item.userLibType().orElse(null));
		if (!telegramPointer) {
			return Optional.empty();
		}
		return Optional.of(ThemeTopicDeepLink.forMessage(chatId, itemId.get()));
	}

	private static String displayTitle(SavedItem item) {
		return item.title()
				.filter(t -> !t.isBlank())
				.orElseGet(() -> snippet(item.bodyText()));
	}

	private static String buildIntroUserMessage(List<CatalogSearchResult.Citation> items) {
		String list = items.stream()
				.map(item -> {
					String type = item.sourceType().map(t -> " (" + t + ")").orElse("");
					return "- " + item.title() + type;
				})
				.collect(Collectors.joining("\n"));
		return "Count: " + items.size() + "\nItems:\n" + list;
	}

	private static String snippet(String body) {
		String trimmed = body.trim().replaceAll("\\s+", " ");
		if (trimmed.length() <= 120) {
			return trimmed;
		}
		return trimmed.substring(0, 117) + "...";
	}
}
