package com.fava.intent;

import com.fava.catalog.Catalog;
import com.fava.ingest.InboxFilingService;
import com.fava.ingest.InboxMessageNormalizer;
import com.fava.ingest.NormalizeResult;
import com.fava.search.CatalogAnswerFormatter;
import com.fava.search.CatalogSearchPort;
import com.fava.search.CatalogSearchResult;

/**
 * Intent Router: classify Action Intent, apply the topic gate, dispatch to ingest/search or notify.
 */
public final class IntentRouter {

	static final String REDIRECT_TO_SMART_SEARCH =
			"That looks like a Catalog Question. Please ask it in the Smart Search topic.";
	static final String REDIRECT_TO_INBOX =
			"That looks like something to save. Please post it in the Inbox topic.";
	static final String CLARIFY =
			"I can save a link or forward in Inbox, or answer a question when you ask in Smart Search. "
					+ "Which did you mean? Please repost clearly in the right topic.";
	static final String NUDGE_SAVE =
			"Looks like a save. Please post that in the Inbox topic.";
	static final String NUDGE_SEARCH =
			"Looks like a Catalog Question. Please ask it in the Smart Search topic.";
	static final String NUDGE_UNCLEAR =
			"Please use Inbox to save links or forwards, or Smart Search to ask questions about your Catalog.";
	static final String INTENT_FAILED =
			"I couldn't tell whether you want to save or search right now. Please try again in a moment.";

	private final ActionIntentClassifier classifier;
	private final InboxMessageNormalizer normalizer;
	private final InboxFilingService filing;
	private final CatalogSearchPort catalogSearch;
	private final ParticipantNotifyPort notify;

	public IntentRouter(
			ActionIntentClassifier classifier,
			InboxMessageNormalizer normalizer,
			InboxFilingService filing,
			CatalogSearchPort catalogSearch,
			ParticipantNotifyPort notify) {
		this.classifier = classifier;
		this.normalizer = normalizer;
		this.filing = filing;
		this.catalogSearch = catalogSearch;
		this.notify = notify;
	}

	public void route(RoutedRequest request, Catalog catalog) {
		ActionIntent intent;
		try {
			intent = classifier.classify(request);
		}
		catch (RuntimeException e) {
			notify.replyToMessage(request.chatId(), request.messageId(), INTENT_FAILED);
			return;
		}

		if (request.locus() == MessageLocus.GENERAL) {
			notify.replyToMessage(request.chatId(), request.messageId(), generalNudge(intent));
			return;
		}

		if (intent == ActionIntent.UNCLEAR) {
			notify.replyToMessage(request.chatId(), request.messageId(), CLARIFY);
			return;
		}

		if (intent == ActionIntent.SAVE && request.locus() == MessageLocus.INBOX) {
			dispatchSave(request, catalog);
			return;
		}
		if (intent == ActionIntent.SEARCH && request.locus() == MessageLocus.SMART_SEARCH) {
			dispatchSearch(request);
			return;
		}

		if (intent == ActionIntent.SEARCH) {
			notify.replyToMessage(request.chatId(), request.messageId(), REDIRECT_TO_SMART_SEARCH);
		}
		else {
			notify.replyToMessage(request.chatId(), request.messageId(), REDIRECT_TO_INBOX);
		}
	}

	private void dispatchSave(RoutedRequest request, Catalog catalog) {
		NormalizeResult normalized = normalizer.normalize(request.toInboxFacts());
		switch (normalized) {
			case NormalizeResult.Rejected rejected ->
					notify.replyToMessage(request.chatId(), request.messageId(), rejected.reason());
			case NormalizeResult.Accepted accepted -> filing.file(accepted.draft(), catalog);
		}
	}

	private void dispatchSearch(RoutedRequest request) {
		String question = request.text();
		if (question == null || question.isBlank()) {
			return;
		}
		CatalogSearchResult result = catalogSearch.answer(request.chatId(), question);
		notify.replyToMessage(
				request.chatId(), request.messageId(), CatalogAnswerFormatter.format(result));
	}

	private static String generalNudge(ActionIntent intent) {
		return switch (intent) {
			case SAVE -> NUDGE_SAVE;
			case SEARCH -> NUDGE_SEARCH;
			case UNCLEAR -> NUDGE_UNCLEAR;
		};
	}
}
