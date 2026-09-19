package com.fava.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fava.catalog.Catalog;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemStore;
import com.fava.catalog.ThemeTopic;
import com.fava.classify.ClassifierDecision;
import com.fava.ingest.ExtractedUrl;
import com.fava.ingest.FilingCallbackButton;
import com.fava.ingest.FilingPort;
import com.fava.ingest.InboxFilingService;
import com.fava.ingest.InboxMessageNormalizer;
import com.fava.search.CatalogSearchPort;
import com.fava.search.CatalogSearchResult;
import com.fava.search.CatalogSearchService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntentRouterTest {

	private static final long CHAT_ID = -1001L;
	private static final long MESSAGE_ID = 50L;
	private static final long INBOX_THREAD = 11L;
	private static final long SMART_SEARCH_THREAD = 22L;

	private ScriptedClassifier classifier;
	private RecordingNotify notify;
	private RecordingFiling filingPort;
	private RecordingSearch search;
	private IntentRouter router;
	private Catalog catalog;

	@BeforeEach
	void setUp() {
		classifier = new ScriptedClassifier();
		notify = new RecordingNotify();
		filingPort = new RecordingFiling();
		search = new RecordingSearch();
		InboxFilingService filing = new InboxFilingService(
				new FakeSavedItemStore(),
				filingPort,
				(draftText, themes) -> new ClassifierDecision.Confident(themes.getFirst()));
		router = new IntentRouter(
				classifier,
				new InboxMessageNormalizer(),
				filing,
				search,
				notify);
		catalog = new Catalog(
				CHAT_ID,
				INBOX_THREAD,
				SMART_SEARCH_THREAD,
				List.of(new ThemeTopic("AI", 31L)));
	}

	@Test
	void inboxSave_dispatchesFiling() {
		classifier.next = ActionIntent.SAVE;
		RoutedRequest request = request(
				MessageLocus.INBOX,
				"https://example.com/a",
				List.of(new ExtractedUrl("https://example.com/a", 0, 19)),
				false);

		router.route(request, catalog);

		assertThat(filingPort.filed).isTrue();
		assertThat(search.calls).isEmpty();
		assertThat(notify.replies).isEmpty();
	}

	@Test
	void smartSearchSearch_dispatchesSearch() {
		classifier.next = ActionIntent.SEARCH;
		RoutedRequest request = request(MessageLocus.SMART_SEARCH, "any pilates tips?", List.of(), false);

		router.route(request, catalog);

		assertThat(search.calls).containsExactly(new RecordingSearch.Call(CHAT_ID, "any pilates tips?"));
		assertThat(filingPort.filed).isFalse();
		assertThat(notify.replies).hasSize(1);
		assertThat(notify.replies.getFirst().text()).isEqualTo(CatalogSearchService.NOTHING_FOUND_MESSAGE);
	}

	@Test
	void inboxSearch_redirectsWithoutDispatch() {
		classifier.next = ActionIntent.SEARCH;
		RoutedRequest request = request(MessageLocus.INBOX, "what did I save about pasta?", List.of(), false);

		router.route(request, catalog);

		assertThat(filingPort.filed).isFalse();
		assertThat(search.calls).isEmpty();
		assertThat(notify.replies).hasSize(1);
		assertThat(notify.replies.getFirst().text()).contains("Smart Search");
	}

	@Test
	void smartSearchSave_redirectsWithoutDispatch() {
		classifier.next = ActionIntent.SAVE;
		RoutedRequest request = request(
				MessageLocus.SMART_SEARCH,
				"https://example.com/b",
				List.of(new ExtractedUrl("https://example.com/b", 0, 19)),
				false);

		router.route(request, catalog);

		assertThat(filingPort.filed).isFalse();
		assertThat(search.calls).isEmpty();
		assertThat(notify.replies).hasSize(1);
		assertThat(notify.replies.getFirst().text()).contains("Inbox");
	}

	@Test
	void unclear_clarifiesWithoutDispatch() {
		classifier.next = ActionIntent.UNCLEAR;
		RoutedRequest request = request(MessageLocus.INBOX, "thanks!", List.of(), false);

		router.route(request, catalog);

		assertThat(filingPort.filed).isFalse();
		assertThat(search.calls).isEmpty();
		assertThat(notify.replies).hasSize(1);
		assertThat(notify.replies.getFirst().text().toLowerCase(Locale.ROOT)).contains("save");
		assertThat(notify.replies.getFirst().text().toLowerCase(Locale.ROOT)).contains("ask");
	}

	@Test
	void generalSave_nudgesWithoutDispatch() {
		classifier.next = ActionIntent.SAVE;
		RoutedRequest request = request(
				MessageLocus.GENERAL,
				"https://example.com/c",
				List.of(new ExtractedUrl("https://example.com/c", 0, 19)),
				false);

		router.route(request, catalog);

		assertThat(filingPort.filed).isFalse();
		assertThat(search.calls).isEmpty();
		assertThat(notify.replies).hasSize(1);
		assertThat(notify.replies.getFirst().text()).contains("Inbox");
	}

	@Test
	void generalSearch_nudgesWithoutDispatch() {
		classifier.next = ActionIntent.SEARCH;
		RoutedRequest request = request(MessageLocus.GENERAL, "recipes for pasta?", List.of(), false);

		router.route(request, catalog);

		assertThat(filingPort.filed).isFalse();
		assertThat(search.calls).isEmpty();
		assertThat(notify.replies).hasSize(1);
		assertThat(notify.replies.getFirst().text()).contains("Smart Search");
	}

	@Test
	void classifierError_failClosedWithoutDispatch() {
		classifier.fail = true;
		RoutedRequest request = request(MessageLocus.INBOX, "https://example.com/d", List.of(), false);

		router.route(request, catalog);

		assertThat(filingPort.filed).isFalse();
		assertThat(search.calls).isEmpty();
		assertThat(notify.replies).hasSize(1);
		assertThat(notify.replies.getFirst().text().toLowerCase(Locale.ROOT)).contains("try again");
	}

	@Test
	void urlBearingSearch_stillDispatchesSearch() {
		classifier.next = ActionIntent.SEARCH;
		String question = "do I have similar recipes https://example.com/recipe?";
		RoutedRequest request = request(
				MessageLocus.SMART_SEARCH,
				question,
				List.of(new ExtractedUrl("https://example.com/recipe", 28, 28)),
				false);

		router.route(request, catalog);

		assertThat(search.calls).containsExactly(new RecordingSearch.Call(CHAT_ID, question));
		assertThat(filingPort.filed).isFalse();
	}

	private static RoutedRequest request(
			MessageLocus locus, String text, List<ExtractedUrl> urls, boolean forwarded) {
		return new RoutedRequest(
				CHAT_ID, MESSAGE_ID, INBOX_THREAD, locus, 7L, text, urls, forwarded, null);
	}

	private static final class ScriptedClassifier implements ActionIntentClassifier {
		ActionIntent next = ActionIntent.UNCLEAR;
		boolean fail;

		@Override
		public ActionIntent classify(RoutedRequest request) {
			if (fail) {
				throw new ActionIntentClassificationException("model down");
			}
			return next;
		}
	}

	private static final class RecordingNotify implements ParticipantNotifyPort {
		final List<Reply> replies = new ArrayList<>();

		@Override
		public void replyToMessage(long chatId, long messageId, String text) {
			replies.add(new Reply(chatId, messageId, text));
		}

		record Reply(long chatId, long messageId, String text) {
		}
	}

	private static final class RecordingFiling implements FilingPort {
		boolean filed;

		@Override
		public void copyMessageToThread(long chatId, long fromMessageId, long toThreadId) {
			filed = true;
		}

		@Override
		public void replyToMessage(long chatId, long messageId, String text) {
			filed = true;
		}

		@Override
		public void replyWithCallbackButtons(
				long chatId, long messageId, String text, List<FilingCallbackButton> buttons) {
			filed = true;
		}

		@Override
		public void answerCallbackQuery(String callbackQueryId) {
		}
	}

	private static final class RecordingSearch implements CatalogSearchPort {
		final List<Call> calls = new ArrayList<>();

		@Override
		public CatalogSearchResult answer(long chatId, String question) {
			calls.add(new Call(chatId, question));
			return new CatalogSearchResult.NothingFound(CatalogSearchService.NOTHING_FOUND_MESSAGE);
		}

		record Call(long chatId, String question) {
		}
	}

	private static final class FakeSavedItemStore implements SavedItemStore {
		private final Map<Long, SavedItem> byId = new LinkedHashMap<>();
		private final AtomicLong nextId = new AtomicLong(1);

		@Override
		public SavedItem save(SavedItem item) {
			SavedItem stored = new SavedItem(
					nextId.getAndIncrement(),
					item.chatId(),
					item.url(),
					item.bodyText(),
					item.themeName(),
					item.sourceMessageId());
			byId.put(stored.id(), stored);
			return stored;
		}

		@Override
		public Optional<SavedItem> findByCatalogAndUrl(long chatId, String url) {
			return Optional.empty();
		}

		@Override
		public Optional<SavedItem> findById(long id) {
			return Optional.ofNullable(byId.get(id));
		}

		@Override
		public List<SavedItem> findByCatalogKeyword(long chatId, String keyword, int limit) {
			return List.of();
		}
	}
}
