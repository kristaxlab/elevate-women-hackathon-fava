package com.fava.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultCatalogSetupServiceTest {

	private static final long CHAT_ID = -100999L;

	private FakeCatalogStore store;
	private FakeForumPort forum;
	private CatalogSetupService setup;

	@BeforeEach
	void setUp() {
		store = new FakeCatalogStore();
		forum = new FakeForumPort();
		setup = new DefaultCatalogSetupService(store, forum);
	}

	@Test
	void setup_withCommaSeparatedThemes_createsInboxSmartSearchAndThemes_thenPersists() {
		forum.forum = true;

		CatalogSetupResult result = setup.setup(CHAT_ID, "AI, Fitness");

		assertThat(result).isInstanceOf(CatalogSetupResult.Success.class);
		Catalog catalog = ((CatalogSetupResult.Success) result).catalog();
		assertThat(catalog.chatId()).isEqualTo(CHAT_ID);
		assertThat(catalog.themes()).containsExactly(
				new ThemeTopic("AI", catalog.themes().get(0).threadId()),
				new ThemeTopic("Fitness", catalog.themes().get(1).threadId()));
		assertThat(forum.createdNames).containsExactly("Inbox", "Smart Search", "AI", "Fitness");
		assertThat(store.findByChatId(CHAT_ID)).contains(catalog);
		assertThat(catalog.inboxThreadId()).isEqualTo(forum.threadIds.get("Inbox"));
		assertThat(catalog.smartSearchThreadId()).isEqualTo(forum.threadIds.get("Smart Search"));
	}

	@Test
	void setup_withNewlineThemes_trimsAndDropsBlanks() {
		forum.forum = true;

		CatalogSetupResult result = setup.setup(CHAT_ID, "  AI \n\n Fitness ,  Cooking  \n");

		assertThat(result).isInstanceOf(CatalogSetupResult.Success.class);
		assertThat(((CatalogSetupResult.Success) result).catalog().themes())
				.extracting(ThemeTopic::name)
				.containsExactly("AI", "Fitness", "Cooking");
	}

	@Test
	void setup_whenNotForum_returnsNeedsForum_withoutCreatingOrPersisting() {
		forum.forum = false;

		CatalogSetupResult result = setup.setup(CHAT_ID, "AI, Fitness");

		assertThat(result).isInstanceOf(CatalogSetupResult.NeedsForum.class);
		assertThat(forum.createdNames).isEmpty();
		assertThat(store.isConfigured(CHAT_ID)).isFalse();
	}

	@Test
	void setup_whenAlreadyConfigured_refusesWithoutCreatingTopics() {
		forum.forum = true;
		Catalog existing = new Catalog(CHAT_ID, 1L, 2L, List.of(new ThemeTopic("AI", 3L)));
		store.create(existing);

		CatalogSetupResult result = setup.setup(CHAT_ID, "Other");

		assertThat(result).isInstanceOf(CatalogSetupResult.AlreadyConfigured.class);
		assertThat(((CatalogSetupResult.AlreadyConfigured) result).existing()).isEqualTo(existing);
		assertThat(forum.createdNames).isEmpty();
	}

	@Test
	void setup_withEmptyThemeList_rejectsWithoutSideEffects() {
		forum.forum = true;

		assertThat(setup.setup(CHAT_ID, "  , \n  ")).isInstanceOf(CatalogSetupResult.EmptyThemeList.class);
		assertThat(setup.setup(CHAT_ID, null)).isInstanceOf(CatalogSetupResult.EmptyThemeList.class);
		assertThat(forum.createdNames).isEmpty();
		assertThat(store.isConfigured(CHAT_ID)).isFalse();
	}

	private static final class FakeForumPort implements CatalogForumPort {
		boolean forum;
		final List<String> createdNames = new ArrayList<>();
		final Map<String, Long> threadIds = new LinkedHashMap<>();
		private final AtomicLong nextId = new AtomicLong(100);

		@Override
		public boolean isForum(long chatId) {
			return forum;
		}

		@Override
		public long createForumTopic(long chatId, String name) {
			createdNames.add(name);
			long id = nextId.getAndIncrement();
			threadIds.put(name, id);
			return id;
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
