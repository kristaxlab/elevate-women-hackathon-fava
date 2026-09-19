package com.fava.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.fava.catalog.Catalog;
import com.fava.catalog.CatalogCreateResult;
import com.fava.catalog.CatalogForumPort;
import com.fava.catalog.CatalogSetupService;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.DefaultCatalogSetupService;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemFilters;
import com.fava.catalog.SavedItemStore;
import com.fava.catalog.ThemeTopic;
import com.fava.classify.FirstThemeTopicClassifier;
import com.fava.ingest.FilingCallbackButton;
import com.fava.ingest.InboxFilingService;
import com.fava.ingest.InboxMessageNormalizer;
import com.fava.ingest.ThemePickCallback;
import com.fava.intent.HeuristicActionIntentClassifier;
import com.fava.intent.IntentRouter;
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
import org.springframework.context.support.StaticMessageSource;

class GroupUpdateHandlerTest {

	private static final long BOT_ID = 9001L;
	private static final long CHAT_ID = -100555L;
	private static final long ADMIN_ID = 42L;
	private static final long MEMBER_ID = 77L;
	private static final long INBOX_THREAD = 11L;
	private static final long SMART_SEARCH_THREAD = 22L;
	private static final long AI_THREAD = 31L;
	private static final long FITNESS_THREAD = 32L;

	private static final String NUDGE = "Nudge: enable Topics then /setup";
	private static final String NEEDS_FORUM = "Checklist: turn on Topics";
	private static final String NOT_ADMIN = "Only admins can /setup";
	private static final String ASK_THEMES = "Send your theme list";
	private static final String EMPTY = "Need at least one theme";
	private static final String ALREADY = "Already configured";
	private static final String SUCCESS = "Ready: {0}";
	private static final String PRE_SETUP = "Ask an admin to /setup";

	private RecordingOutbound outbound;
	private FakeCatalogStore store;
	private FakeSavedItemStore savedItems;
	private FakeForumPort forum;
	private FakeAdminPort admins;
	private ScriptedCatalogSearch catalogSearch;
	private GroupUpdateHandler handler;

	@BeforeEach
	void setUp() {
		outbound = new RecordingOutbound();
		store = new FakeCatalogStore();
		savedItems = new FakeSavedItemStore();
		forum = new FakeForumPort();
		forum.forum = true;
		admins = new FakeAdminPort();
		admins.adminIds.add(ADMIN_ID);
		catalogSearch = new ScriptedCatalogSearch();
		CatalogSetupService setup = new DefaultCatalogSetupService(store, forum);
		InboxFilingService filing = new InboxFilingService(savedItems, outbound, new FirstThemeTopicClassifier());
		StaticMessageSource messages = messages();
		handler = new GroupUpdateHandler(
				messages,
				outbound,
				store,
				setup,
				admins,
				() -> BOT_ID,
				filing,
				intentRouter(filing));
	}

	private IntentRouter intentRouter(InboxFilingService filing) {
		return new IntentRouter(
				new HeuristicActionIntentClassifier(),
				new InboxMessageNormalizer(),
				filing,
				catalogSearch,
				outbound::replyText);
	}

	@Test
	void botBecomesAdmin_sendsTopicsAndSetupNudge() {
		handler.handle(myChatMemberUpdate("member", "administrator"));

		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(CHAT_ID, NUDGE));
	}

	@Test
	void botAlreadyAdmin_doesNotNudgeAgain() {
		handler.handle(myChatMemberUpdate("administrator", "administrator"));

		assertThat(outbound.plainSent).isEmpty();
	}

	@Test
	void setupByNonAdmin_isRejected() {
		handler.handle(groupText(MEMBER_ID, "/setup AI, Fitness", List.of(botCommand(0, 6)), null));

		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(CHAT_ID, NOT_ADMIN));
		assertThat(store.isConfigured(CHAT_ID)).isFalse();
		assertThat(forum.createdNames).isEmpty();
	}

	@Test
	void setupWithThemesOnSameLine_createsTopicsAndPersists() {
		handler.handle(groupText(ADMIN_ID, "/setup AI, Fitness", List.of(botCommand(0, 6)), null));

		assertThat(store.isConfigured(CHAT_ID)).isTrue();
		assertThat(forum.createdNames).containsExactly("Inbox", "Smart Search", "AI", "Fitness");
		assertThat(outbound.plainSent).hasSize(1);
		assertThat(outbound.plainSent.getFirst().text()).isEqualTo("Ready: AI, Fitness");
	}

	@Test
	void setupBareThenThemeListOnNextMessage_completesSetup() {
		handler.handle(groupText(ADMIN_ID, "/setup", List.of(botCommand(0, 6)), null));
		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(CHAT_ID, ASK_THEMES));

		handler.handle(groupText(ADMIN_ID, "AI\nFitness", List.of(), null));

		assertThat(store.isConfigured(CHAT_ID)).isTrue();
		assertThat(forum.createdNames).containsExactly("Inbox", "Smart Search", "AI", "Fitness");
	}

	@Test
	void setupWhenNotForum_sendsChecklistWithoutPersisting() {
		forum.forum = false;

		handler.handle(groupText(ADMIN_ID, "/setup AI", List.of(botCommand(0, 6)), null));

		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(CHAT_ID, NEEDS_FORUM));
		assertThat(store.isConfigured(CHAT_ID)).isFalse();
		assertThat(forum.createdNames).isEmpty();
	}

	@Test
	void secondSetup_isRejected() {
		handler.handle(groupText(ADMIN_ID, "/setup AI", List.of(botCommand(0, 6)), null));
		outbound.plainSent.clear();
		forum.createdNames.clear();

		handler.handle(groupText(ADMIN_ID, "/setup Other", List.of(botCommand(0, 6)), null));

		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(CHAT_ID, ALREADY));
		assertThat(forum.createdNames).isEmpty();
	}

	@Test
	void unconfiguredGroupMessage_promptsForSetup() {
		handler.handle(groupText(MEMBER_ID, "where is search?", List.of(), null));

		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(CHAT_ID, PRE_SETUP));
	}

	@Test
	void configuredGroupNonInboxMessage_isSilent() {
		seedConfiguredCatalog();

		handler.handle(groupText(MEMBER_ID, "hello", List.of(), FITNESS_THREAD));

		assertThat(outbound.plainSent).isEmpty();
		assertThat(outbound.replies).isEmpty();
		assertThat(outbound.copies).isEmpty();
	}

	@Test
	void inboxUrl_isFiledIntoFirstThemeWithConfirmation() {
		seedConfiguredCatalog();

		handler.handle(groupText(
				MEMBER_ID,
				"https://www.instagram.com/p/XYZ/",
				List.of(),
				INBOX_THREAD));

		assertThat(savedItems.findByCatalogAndUrl(CHAT_ID, "https://www.instagram.com/p/XYZ/")).isPresent();
		assertThat(outbound.copies).containsExactly(new RecordingOutbound.Copy(CHAT_ID, 5L, AI_THREAD));
		assertThat(outbound.replies).containsExactly(
				new RecordingOutbound.Reply(CHAT_ID, 5L, "Filed → AI"));
	}

	@Test
	void inboxUnsupported_getsClarifyWhenIntentUnclear() {
		seedConfiguredCatalog();

		handler.handle(groupText(MEMBER_ID, "just a note", List.of(), INBOX_THREAD));

		assertThat(outbound.copies).isEmpty();
		assertThat(outbound.replies).hasSize(1);
		assertThat(outbound.replies.getFirst().text().toLowerCase(Locale.ROOT)).contains("save");
		assertThat(savedItems.byId).isEmpty();
	}

	@Test
	void inboxForwardWithoutUrl_isAcceptedAndFiled() {
		seedConfiguredCatalog();

		handler.handle(groupForward(MEMBER_ID, "Channel tip about form", INBOX_THREAD));

		assertThat(outbound.copies).containsExactly(new RecordingOutbound.Copy(CHAT_ID, 5L, AI_THREAD));
		assertThat(outbound.replies).containsExactly(
				new RecordingOutbound.Reply(CHAT_ID, 5L, "Filed → AI"));
	}

	@Test
	void inboxDuplicateUrl_reportsExistingThemeWithoutCopy() {
		seedConfiguredCatalog();
		savedItems.save(SavedItem.of(
				null,
				CHAT_ID,
				Optional.of("https://dup.example/a"),
				"first",
				"Fitness",
				1L));

		handler.handle(groupText(MEMBER_ID, "https://dup.example/a", List.of(), INBOX_THREAD));

		assertThat(outbound.copies).isEmpty();
		assertThat(outbound.replies).containsExactly(
				new RecordingOutbound.Reply(CHAT_ID, 5L, "Already saved → Fitness"));
	}

	@Test
	void smartSearchQuestion_repliesWithCatalogAnswerAndDoesNotFile() {
		seedConfiguredCatalog();
		catalogSearch.next = new CatalogSearchResult.Answer(
				"You saved a pilates tip.",
				List.of(new CatalogSearchResult.Citation(
						"Pilates reformer tip",
						Optional.of("article"),
						Optional.of("https://example.com/pilates"),
						Optional.of("https://t.me/c/123/9001"))));

		handler.handle(groupText(MEMBER_ID, "any pilates tips?", List.of(), SMART_SEARCH_THREAD));

		assertThat(outbound.replies).hasSize(1);
		assertThat(outbound.replies.getFirst().text()).contains("You saved a pilates tip.");
		assertThat(outbound.replies.getFirst().text()).contains("Pilates reformer tip");
		assertThat(outbound.replies.getFirst().text()).contains("https://t.me/c/123/9001");
		assertThat(outbound.copies).isEmpty();
		assertThat(savedItems.byId).isEmpty();
		assertThat(catalogSearch.lastChatId).isEqualTo(CHAT_ID);
		assertThat(catalogSearch.lastQuestion).isEqualTo("any pilates tips?");
	}

	@Test
	void smartSearchEmptyCatalog_repliesHonestNothingFound_notInboxIngest() {
		seedConfiguredCatalog();
		catalogSearch.next = new CatalogSearchResult.NothingFound(CatalogSearchService.NOTHING_FOUND_MESSAGE);

		handler.handle(groupText(MEMBER_ID, "what about cooking?", List.of(), SMART_SEARCH_THREAD));

		assertThat(outbound.replies).containsExactly(
				new RecordingOutbound.Reply(CHAT_ID, 5L, CatalogSearchService.NOTHING_FOUND_MESSAGE));
		assertThat(outbound.copies).isEmpty();
	}

	@Test
	void themePickCallback_completesFiling() {
		seedConfiguredCatalog();
		InboxFilingService filing = new InboxFilingService(
				savedItems,
				outbound,
				(text, themes) -> new com.fava.classify.ClassifierDecision.NeedsUserPick());
		handler = new GroupUpdateHandler(
				messages(),
				outbound,
				store,
				new DefaultCatalogSetupService(store, forum),
				admins,
				() -> BOT_ID,
				filing,
				intentRouter(filing));

		handler.handle(groupText(MEMBER_ID, "https://example.com/pick-me", List.of(), INBOX_THREAD));
		assertThat(outbound.callbackReplies).hasSize(1);
		assertThat(savedItems.byId).isEmpty();

		String callbackData = ThemePickCallback.encode(5L, 1);
		handler.handle(themePickCallback("cq-9", callbackData));

		assertThat(savedItems.findByCatalogAndUrl(CHAT_ID, "https://example.com/pick-me"))
				.isPresent()
				.get()
				.extracting(SavedItem::themeName)
				.isEqualTo("Fitness");
		assertThat(outbound.copies).containsExactly(new RecordingOutbound.Copy(CHAT_ID, 5L, FITNESS_THREAD));
		assertThat(outbound.replies).containsExactly(
				new RecordingOutbound.Reply(CHAT_ID, 5L, "Filed → Fitness"));
		assertThat(outbound.answeredCallbacks).containsExactly("cq-9");
	}

	@Test
	void privateChat_isIgnored() {
		handler.handle(new TelegramUpdate(
				1L,
				new TelegramMessage(1L, new TelegramChat(9L, "private"), new TelegramUser(ADMIN_ID, false, "a"),
						"/setup AI", List.of(botCommand(0, 6)), null)));

		assertThat(outbound.plainSent).isEmpty();
	}

	private void seedConfiguredCatalog() {
		store.create(new Catalog(
				CHAT_ID,
				INBOX_THREAD,
				SMART_SEARCH_THREAD,
				List.of(new ThemeTopic("AI", AI_THREAD), new ThemeTopic("Fitness", FITNESS_THREAD))));
	}

	private static StaticMessageSource messages() {
		StaticMessageSource messages = new StaticMessageSource();
		messages.addMessage(GroupUpdateHandler.MSG_ADMIN_NUDGE, Locale.ENGLISH, NUDGE);
		messages.addMessage(GroupUpdateHandler.MSG_NEEDS_FORUM, Locale.ENGLISH, NEEDS_FORUM);
		messages.addMessage(GroupUpdateHandler.MSG_NOT_ADMIN, Locale.ENGLISH, NOT_ADMIN);
		messages.addMessage(GroupUpdateHandler.MSG_ASK_THEMES, Locale.ENGLISH, ASK_THEMES);
		messages.addMessage(GroupUpdateHandler.MSG_EMPTY_THEMES, Locale.ENGLISH, EMPTY);
		messages.addMessage(GroupUpdateHandler.MSG_ALREADY, Locale.ENGLISH, ALREADY);
		messages.addMessage(GroupUpdateHandler.MSG_SUCCESS, Locale.ENGLISH, SUCCESS);
		messages.addMessage(GroupUpdateHandler.MSG_PRE_SETUP, Locale.ENGLISH, PRE_SETUP);
		return messages;
	}

	private static TelegramUpdate myChatMemberUpdate(String oldStatus, String newStatus) {
		TelegramUser bot = new TelegramUser(BOT_ID, true, "fava_bot");
		return new TelegramUpdate(
				10L,
				null,
				new TelegramChatMemberUpdated(
						new TelegramChat(CHAT_ID, "supergroup"),
						new TelegramUser(ADMIN_ID, false, "owner"),
						new TelegramChatMember(bot, oldStatus),
						new TelegramChatMember(bot, newStatus)));
	}

	private static TelegramUpdate groupText(
			long fromId, String text, List<TelegramMessageEntity> entities, Long threadId) {
		return new TelegramUpdate(
				1L,
				new TelegramMessage(
						5L,
						new TelegramChat(CHAT_ID, "supergroup"),
						new TelegramUser(fromId, false, "u"),
						text,
						entities,
						threadId));
	}

	private static TelegramUpdate groupForward(long fromId, String text, long threadId) {
		return new TelegramUpdate(
				1L,
				new TelegramMessage(
						5L,
						new TelegramChat(CHAT_ID, "supergroup"),
						new TelegramUser(fromId, false, "u"),
						text,
						null,
						List.of(),
						null,
						threadId,
						1_700_000_000,
						new TelegramUser(999L, false, "channel_author"),
						null));
	}

	private static TelegramUpdate themePickCallback(String callbackId, String data) {
		TelegramMessage message = new TelegramMessage(
				99L,
				new TelegramChat(CHAT_ID, "supergroup"),
				new TelegramUser(MEMBER_ID, false, "u"),
				"Which Theme Topic?",
				List.of(),
				INBOX_THREAD);
		return new TelegramUpdate(
				2L,
				null,
				null,
				new TelegramCallbackQuery(
						callbackId,
						new TelegramUser(MEMBER_ID, false, "u"),
						message,
						"instance",
						data));
	}

	private static TelegramMessageEntity botCommand(int offset, int length) {
		return new TelegramMessageEntity("bot_command", offset, length);
	}

	private static final class RecordingOutbound implements TelegramOutbound, com.fava.ingest.FilingPort {
		final List<PlainSent> plainSent = new ArrayList<>();
		final List<Reply> replies = new ArrayList<>();
		final List<Copy> copies = new ArrayList<>();
		final List<CallbackReply> callbackReplies = new ArrayList<>();
		final List<String> answeredCallbacks = new ArrayList<>();

		@Override
		public void sendText(long chatId, String text) {
			plainSent.add(new PlainSent(chatId, text));
		}

		@Override
		public void sendTextWithInlineKeyboard(long chatId, String text, List<InlineUrlButton> buttons) {
		}

		@Override
		public void replyText(long chatId, long replyToMessageId, String text) {
			replies.add(new Reply(chatId, replyToMessageId, text));
		}

		@Override
		public void replyTextWithCallbackButtons(
				long chatId, long replyToMessageId, String text, List<InlineCallbackButton> buttons) {
			callbackReplies.add(new CallbackReply(chatId, replyToMessageId, text, List.copyOf(buttons)));
		}

		@Override
		public void answerCallbackQuery(String callbackQueryId) {
			answeredCallbacks.add(callbackQueryId);
		}

		@Override
		public void replyWithCallbackButtons(
				long chatId, long replyToMessageId, String text, List<FilingCallbackButton> buttons) {
			List<InlineCallbackButton> mapped = buttons.stream()
					.map(b -> new InlineCallbackButton(b.label(), b.callbackData()))
					.toList();
			replyTextWithCallbackButtons(chatId, replyToMessageId, text, mapped);
		}

		@Override
		public void copyMessage(long chatId, long fromMessageId, long toMessageThreadId) {
			copyMessageToThread(chatId, fromMessageId, toMessageThreadId);
		}

		@Override
		public Optional<Long> copyMessageToThread(long chatId, long fromMessageId, long messageThreadId) {
			copies.add(new Copy(chatId, fromMessageId, messageThreadId));
			return Optional.of(777L);
		}

		@Override
		public void replyToMessage(long chatId, long replyToMessageId, String text) {
			replyText(chatId, replyToMessageId, text);
		}

		record PlainSent(long chatId, String text) {
		}

		record Reply(long chatId, long replyToMessageId, String text) {
		}

		record Copy(long chatId, long fromMessageId, long messageThreadId) {
		}

		record CallbackReply(long chatId, long replyToMessageId, String text, List<InlineCallbackButton> buttons) {
		}
	}

	private static final class FakeAdminPort implements ChatAdminPort {
		final java.util.Set<Long> adminIds = new java.util.HashSet<>();

		@Override
		public boolean isAdmin(long chatId, long userId) {
			return adminIds.contains(userId);
		}
	}

	private static final class FakeForumPort implements CatalogForumPort {
		boolean forum;
		final List<String> createdNames = new ArrayList<>();
		private final AtomicLong nextId = new AtomicLong(100);

		@Override
		public boolean isForum(long chatId) {
			return forum;
		}

		@Override
		public long createForumTopic(long chatId, String name) {
			createdNames.add(name);
			return nextId.getAndIncrement();
		}
	}

	private static final class FakeCatalogStore implements CatalogStore {
		private final Map<Long, Catalog> byChat = new LinkedHashMap<>();

		@Override
		public CatalogCreateResult create(Catalog catalog) {
			if (byChat.containsKey(catalog.chatId())) {
				return new CatalogCreateResult.AlreadyConfigured(byChat.get(catalog.chatId()));
			}
			byChat.put(catalog.chatId(), catalog);
			return new CatalogCreateResult.Created(catalog);
		}

		@Override
		public Optional<Catalog> findByChatId(long chatId) {
			return Optional.ofNullable(byChat.get(chatId));
		}

		@Override
		public boolean isConfigured(long chatId) {
			return byChat.containsKey(chatId);
		}
	}

	private static final class FakeSavedItemStore implements SavedItemStore {
		final Map<Long, SavedItem> byId = new LinkedHashMap<>();
		private final AtomicLong nextId = new AtomicLong(1);

		@Override
		public SavedItem save(SavedItem item) {
			SavedItem stored = new SavedItem(
					nextId.getAndIncrement(),
					item.chatId(),
					item.url(),
					item.bodyText(),
					item.themeName(),
					item.sourceMessageId(),
					item.userLibType(),
					item.userLibItemId(),
					item.sourceType(),
					item.title(),
					item.recommendedBy(),
					item.tags(),
					item.searchText());
			byId.put(stored.id(), stored);
			return stored;
		}

		@Override
		public SavedItem updateUserLib(long id, String userLibType, String userLibItemId) {
			SavedItem existing = byId.get(id);
			if (existing == null) {
				throw new IllegalStateException("missing saved item " + id);
			}
			SavedItem updated = new SavedItem(
					existing.id(),
					existing.chatId(),
					existing.url(),
					existing.bodyText(),
					existing.themeName(),
					existing.sourceMessageId(),
					Optional.of(userLibType),
					Optional.of(userLibItemId),
					existing.sourceType(),
					existing.title(),
					existing.recommendedBy(),
					existing.tags(),
					existing.searchText());
			byId.put(id, updated);
			return updated;
		}

		@Override
		public Optional<SavedItem> findByCatalogAndUrl(long chatId, String url) {
			return byId.values().stream()
					.filter(i -> i.chatId() == chatId && i.url().isPresent() && i.url().get().equals(url))
					.findFirst();
		}

		@Override
		public Optional<SavedItem> findById(long id) {
			return Optional.ofNullable(byId.get(id));
		}

		@Override
		public List<SavedItem> findByCatalogFilters(long chatId, SavedItemFilters filters) {
			return byId.values().stream().filter(i -> i.chatId() == chatId).toList();
		}
	}

	private static final class ScriptedCatalogSearch implements CatalogSearchPort {
		CatalogSearchResult next = new CatalogSearchResult.NothingFound(CatalogSearchService.NOTHING_FOUND_MESSAGE);
		Long lastChatId;
		String lastQuestion;

		@Override
		public CatalogSearchResult answer(long chatId, String question) {
			lastChatId = chatId;
			lastQuestion = question;
			return next;
		}
	}
}
