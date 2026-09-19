package com.fava.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.fava.catalog.Catalog;
import com.fava.catalog.CatalogCreateResult;
import com.fava.catalog.CatalogForumPort;
import com.fava.catalog.CatalogSetupService;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.DefaultCatalogSetupService;
import com.fava.catalog.ThemeTopic;
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
	private FakeForumPort forum;
	private FakeAdminPort admins;
	private GroupUpdateHandler handler;

	@BeforeEach
	void setUp() {
		outbound = new RecordingOutbound();
		store = new FakeCatalogStore();
		forum = new FakeForumPort();
		forum.forum = true;
		admins = new FakeAdminPort();
		admins.adminIds.add(ADMIN_ID);
		CatalogSetupService setup = new DefaultCatalogSetupService(store, forum);
		StaticMessageSource messages = messages();
		handler = new GroupUpdateHandler(messages, outbound, store, setup, admins, () -> BOT_ID);
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
		handler.handle(groupText(MEMBER_ID, "/setup AI, Fitness", List.of(botCommand(0, 6))));

		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(CHAT_ID, NOT_ADMIN));
		assertThat(store.isConfigured(CHAT_ID)).isFalse();
		assertThat(forum.createdNames).isEmpty();
	}

	@Test
	void setupWithThemesOnSameLine_createsTopicsAndPersists() {
		handler.handle(groupText(ADMIN_ID, "/setup AI, Fitness", List.of(botCommand(0, 6))));

		assertThat(store.isConfigured(CHAT_ID)).isTrue();
		assertThat(forum.createdNames).containsExactly("Inbox", "Smart Search", "AI", "Fitness");
		assertThat(outbound.plainSent).hasSize(1);
		assertThat(outbound.plainSent.getFirst().text()).isEqualTo("Ready: AI, Fitness");
	}

	@Test
	void setupBareThenThemeListOnNextMessage_completesSetup() {
		handler.handle(groupText(ADMIN_ID, "/setup", List.of(botCommand(0, 6))));
		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(CHAT_ID, ASK_THEMES));

		handler.handle(groupText(ADMIN_ID, "AI\nFitness", List.of()));

		assertThat(store.isConfigured(CHAT_ID)).isTrue();
		assertThat(forum.createdNames).containsExactly("Inbox", "Smart Search", "AI", "Fitness");
	}

	@Test
	void setupWhenNotForum_sendsChecklistWithoutPersisting() {
		forum.forum = false;

		handler.handle(groupText(ADMIN_ID, "/setup AI", List.of(botCommand(0, 6))));

		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(CHAT_ID, NEEDS_FORUM));
		assertThat(store.isConfigured(CHAT_ID)).isFalse();
		assertThat(forum.createdNames).isEmpty();
	}

	@Test
	void secondSetup_isRejected() {
		handler.handle(groupText(ADMIN_ID, "/setup AI", List.of(botCommand(0, 6))));
		outbound.plainSent.clear();
		forum.createdNames.clear();

		handler.handle(groupText(ADMIN_ID, "/setup Other", List.of(botCommand(0, 6))));

		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(CHAT_ID, ALREADY));
		assertThat(forum.createdNames).isEmpty();
	}

	@Test
	void unconfiguredGroupMessage_promptsForSetup() {
		handler.handle(groupText(MEMBER_ID, "where is search?", List.of()));

		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(CHAT_ID, PRE_SETUP));
	}

	@Test
	void configuredGroupNonSetupMessage_isSilent() {
		store.create(new Catalog(CHAT_ID, 1L, 2L, List.of(new ThemeTopic("AI", 3L))));

		handler.handle(groupText(MEMBER_ID, "hello", List.of()));

		assertThat(outbound.plainSent).isEmpty();
	}

	@Test
	void privateChat_isIgnored() {
		handler.handle(new TelegramUpdate(
				1L,
				new TelegramMessage(1L, new TelegramChat(9L, "private"), new TelegramUser(ADMIN_ID, false, "a"),
						"/setup AI", List.of(botCommand(0, 6)), null)));

		assertThat(outbound.plainSent).isEmpty();
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

	private static TelegramUpdate groupText(long fromId, String text, List<TelegramMessageEntity> entities) {
		return new TelegramUpdate(
				1L,
				new TelegramMessage(
						5L,
						new TelegramChat(CHAT_ID, "supergroup"),
						new TelegramUser(fromId, false, "u"),
						text,
						entities,
						null));
	}

	private static TelegramMessageEntity botCommand(int offset, int length) {
		return new TelegramMessageEntity("bot_command", offset, length);
	}

	private static final class RecordingOutbound implements TelegramOutbound {
		final List<PlainSent> plainSent = new ArrayList<>();

		@Override
		public void sendText(long chatId, String text) {
			plainSent.add(new PlainSent(chatId, text));
		}

		@Override
		public void sendTextWithInlineKeyboard(long chatId, String text, List<InlineUrlButton> buttons) {
		}

		record PlainSent(long chatId, String text) {
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
}
