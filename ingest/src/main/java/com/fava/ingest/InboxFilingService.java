package com.fava.ingest;

import com.fava.catalog.Catalog;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemIndexer;
import com.fava.catalog.SavedItemStore;
import com.fava.catalog.ThemeTopic;
import com.fava.classify.ClassifierDecision;
import com.fava.classify.TopicClassifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Files an Accepted Inbox draft: URL dedupe, Classifier Decision, persist, copy, confirm;
 * or ask for a Theme Topic pick when confidence is low, then complete Filing on callback.
 * Newly filed Saved Items are indexed for Smart Search (index-on-save; no backfill of older rows).
 */
public final class InboxFilingService {

	static final String FILED_PREFIX = "Filed → ";
	static final String ALREADY_PREFIX = "Already saved → ";
	static final String PICK_PROMPT = "Which Theme Topic should I file this under?";

	private final SavedItemStore savedItems;
	private final FilingPort filingPort;
	private final TopicClassifier topicClassifier;
	private final SavedItemIndexer savedItemIndexer;
	private final SavedItemEnricher savedItemEnricher;

	/** Pending drafts awaiting Theme Topic pick: key = chatId + ':' + sourceMessageId. */
	private final Map<String, AcceptedDraft> pendingBySource = new ConcurrentHashMap<>();

	public InboxFilingService(
			SavedItemStore savedItems, FilingPort filingPort, TopicClassifier topicClassifier) {
		this(savedItems, filingPort, topicClassifier, item -> {}, new HeuristicSavedItemEnricher());
	}

	public InboxFilingService(
			SavedItemStore savedItems,
			FilingPort filingPort,
			TopicClassifier topicClassifier,
			SavedItemIndexer savedItemIndexer) {
		this(savedItems, filingPort, topicClassifier, savedItemIndexer, new HeuristicSavedItemEnricher());
	}

	public InboxFilingService(
			SavedItemStore savedItems,
			FilingPort filingPort,
			TopicClassifier topicClassifier,
			SavedItemIndexer savedItemIndexer,
			SavedItemEnricher savedItemEnricher) {
		this.savedItems = savedItems;
		this.filingPort = filingPort;
		this.topicClassifier = topicClassifier;
		this.savedItemIndexer = savedItemIndexer;
		this.savedItemEnricher = savedItemEnricher;
	}

	public FilingResult file(AcceptedDraft draft, Catalog catalog) {
		if (draft.url().isPresent()) {
			Optional<SavedItem> existing = savedItems.findByCatalogAndUrl(catalog.chatId(), draft.url().get());
			if (existing.isPresent()) {
				String themeName = existing.get().themeName();
				filingPort.replyToMessage(draft.chatId(), draft.sourceMessageId(), ALREADY_PREFIX + themeName);
				return new FilingResult.AlreadyFiled(themeName);
			}
		}

		List<ThemeTopic> themes = catalog.themes();
		if (themes.isEmpty()) {
			throw new IllegalStateException("Catalog has no Theme Topics to file into");
		}

		List<String> themeNames = themes.stream().map(ThemeTopic::name).toList();
		ClassifierDecision decision = topicClassifier.classify(draftText(draft), themeNames);

		return switch (decision) {
			case ClassifierDecision.Confident confident -> fileToTheme(draft, catalog, confident.themeName());
			case ClassifierDecision.NeedsUserPick ignored -> askThemePick(draft, themes);
		};
	}

	/**
	 * Completes Filing after a Theme Topic inline-button pick. Answers the callback query.
	 */
	public FilingResult completeThemePick(
			String callbackQueryId, long chatId, long sourceMessageId, int themeIndex, Catalog catalog) {
		try {
			AcceptedDraft draft = pendingBySource.remove(pendingKey(chatId, sourceMessageId));
			if (draft == null) {
				return new FilingResult.AwaitingThemePick();
			}
			List<ThemeTopic> themes = catalog.themes();
			if (themeIndex < 0 || themeIndex >= themes.size()) {
				pendingBySource.put(pendingKey(chatId, sourceMessageId), draft);
				return new FilingResult.AwaitingThemePick();
			}
			return fileToTheme(draft, catalog, themes.get(themeIndex).name());
		}
		finally {
			if (callbackQueryId != null && !callbackQueryId.isBlank()) {
				filingPort.answerCallbackQuery(callbackQueryId);
			}
		}
	}

	private FilingResult askThemePick(AcceptedDraft draft, List<ThemeTopic> themes) {
		pendingBySource.put(pendingKey(draft.chatId(), draft.sourceMessageId()), draft);
		List<FilingCallbackButton> buttons = IntStream.range(0, themes.size())
				.mapToObj(i -> new FilingCallbackButton(
						themes.get(i).name(),
						ThemePickCallback.encode(draft.sourceMessageId(), i)))
				.toList();
		filingPort.replyWithCallbackButtons(
				draft.chatId(), draft.sourceMessageId(), PICK_PROMPT, buttons);
		return new FilingResult.AwaitingThemePick();
	}

	private FilingResult fileToTheme(AcceptedDraft draft, Catalog catalog, String themeName) {
		ThemeTopic theme = findTheme(catalog, themeName)
				.orElseThrow(() -> new IllegalStateException("Unknown Theme Topic: " + themeName));
		SavedItemEnrichment enrichment = enrichSafely(draft);
		SavedItem saved = savedItems.save(new SavedItem(
				null,
				catalog.chatId(),
				draft.url(),
				draft.bodyText(),
				theme.name(),
				draft.sourceMessageId(),
				Optional.empty(),
				Optional.empty(),
				enrichment.sourceType(),
				enrichment.title(),
				enrichment.recommendedBy(),
				enrichment.tags(),
				enrichment.searchText()));
		Optional<Long> copyMessageId =
				filingPort.copyMessageToThread(catalog.chatId(), draft.sourceMessageId(), theme.threadId());
		if (copyMessageId.isPresent()) {
			saved = savedItems.updateUserLib(
					saved.id(), SavedItem.USER_LIB_TYPE_TELEGRAM, Long.toString(copyMessageId.get()));
		}
		savedItemIndexer.index(saved);
		filingPort.replyToMessage(draft.chatId(), draft.sourceMessageId(), FILED_PREFIX + theme.name());
		return new FilingResult.Filed(theme.name(), saved);
	}

	private SavedItemEnrichment enrichSafely(AcceptedDraft draft) {
		try {
			return savedItemEnricher.enrich(draft.url(), draft.bodyText());
		}
		catch (RuntimeException e) {
			return SavedItemEnrichment.empty();
		}
	}

	private static Optional<ThemeTopic> findTheme(Catalog catalog, String themeName) {
		return catalog.themes().stream()
				.filter(t -> t.name().equals(themeName))
				.findFirst();
	}

	private static String draftText(AcceptedDraft draft) {
		if (draft.url().isPresent()) {
			return draft.bodyText() + "\nURL: " + draft.url().get();
		}
		return draft.bodyText();
	}

	private static String pendingKey(long chatId, long sourceMessageId) {
		return chatId + ":" + sourceMessageId;
	}
}
