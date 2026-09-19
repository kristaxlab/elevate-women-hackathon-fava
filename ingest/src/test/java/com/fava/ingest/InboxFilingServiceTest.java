package com.fava.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fava.catalog.Catalog;
import com.fava.catalog.CatalogSchema;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.JdbcCatalogStore;
import com.fava.catalog.JdbcSavedItemStore;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemStore;
import com.fava.catalog.ThemeTopic;
import com.fava.classify.ClassifierDecision;
import com.fava.classify.TopicClassifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class InboxFilingServiceTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private static final long CHAT_ID = -100555L;
	private static final long INBOX_THREAD = 11L;
	private static final long SMART_SEARCH_THREAD = 22L;

	private Catalog catalog;
	private SavedItemStore savedItemStore;
	private RecordingFilingPort telegram;
	private ScriptedClassifier classifier;
	private InboxFilingService filing;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource);
		new JdbcTemplate(dataSource).execute("TRUNCATE saved_items, theme_topics, catalogs CASCADE");
		CatalogStore catalogStore = new JdbcCatalogStore(dataSource);
		savedItemStore = new JdbcSavedItemStore(dataSource);
		catalog = new Catalog(
				CHAT_ID,
				INBOX_THREAD,
				SMART_SEARCH_THREAD,
				List.of(new ThemeTopic("AI", 31L), new ThemeTopic("Fitness", 32L)));
		catalogStore.create(catalog);
		telegram = new RecordingFilingPort();
		classifier = new ScriptedClassifier(new ClassifierDecision.Confident("AI"));
		filing = new InboxFilingService(savedItemStore, telegram, classifier);
	}

	@Test
	void confidentDecision_filesChosenTheme_persists_copiesAndReplies() {
		classifier.next = new ClassifierDecision.Confident("Fitness");
		telegram.nextCopyMessageId = Optional.of(9001L);
		AcceptedDraft draft = new AcceptedDraft(
				CHAT_ID,
				7L,
				INBOX_THREAD,
				Optional.of("https://www.instagram.com/p/NEW/"),
				"Check this https://www.instagram.com/p/NEW/");

		FilingResult result = filing.file(draft, catalog);

		assertThat(result).isInstanceOf(FilingResult.Filed.class);
		assertThat(((FilingResult.Filed) result).themeName()).isEqualTo("Fitness");
		SavedItem stored = savedItemStore.findByCatalogAndUrl(CHAT_ID, "https://www.instagram.com/p/NEW/")
				.orElseThrow();
		assertThat(stored.themeName()).isEqualTo("Fitness");
		assertThat(stored.sourceMessageId()).isEqualTo(7L);
		assertThat(stored.userLibType()).contains("telegram");
		assertThat(stored.userLibItemId()).contains("9001");
		assertThat(((FilingResult.Filed) result).item()).isEqualTo(stored);
		assertThat(telegram.copies).containsExactly(new RecordingFilingPort.Copy(CHAT_ID, 7L, 32L));
		assertThat(telegram.replies).containsExactly(
				new RecordingFilingPort.Reply(CHAT_ID, 7L, "Filed → Fitness"));
		assertThat(telegram.buttonReplies).isEmpty();
	}

	@Test
	void confidentDecision_whenCopyMessageIdMissing_stillFilesWithoutUserLibPointer() {
		classifier.next = new ClassifierDecision.Confident("AI");
		telegram.nextCopyMessageId = Optional.empty();
		AcceptedDraft draft = new AcceptedDraft(
				CHAT_ID,
				11L,
				INBOX_THREAD,
				Optional.of("https://example.com/no-copy-id"),
				"https://example.com/no-copy-id");

		FilingResult result = filing.file(draft, catalog);

		assertThat(result).isInstanceOf(FilingResult.Filed.class);
		SavedItem stored = savedItemStore.findByCatalogAndUrl(CHAT_ID, "https://example.com/no-copy-id")
				.orElseThrow();
		assertThat(stored.sourceMessageId()).isEqualTo(11L);
		assertThat(stored.userLibType()).isEmpty();
		assertThat(stored.userLibItemId()).isEmpty();
		assertThat(telegram.replies).containsExactly(
				new RecordingFilingPort.Reply(CHAT_ID, 11L, "Filed → AI"));
	}

	@Test
	void duplicateUrlInSameCatalog_skipsClassifyCopyAndReportsExistingTheme() {
		savedItemStore.save(SavedItem.of(
				null,
				CHAT_ID,
				Optional.of("https://dup.example/x"),
				"already there",
				"Fitness",
				1L));
		AcceptedDraft draft = new AcceptedDraft(
				CHAT_ID,
				99L,
				INBOX_THREAD,
				Optional.of("https://dup.example/x"),
				"again https://dup.example/x");

		FilingResult result = filing.file(draft, catalog);

		assertThat(result).isInstanceOf(FilingResult.AlreadyFiled.class);
		assertThat(((FilingResult.AlreadyFiled) result).themeName()).isEqualTo("Fitness");
		assertThat(classifier.calls.get()).isZero();
		assertThat(telegram.copies).isEmpty();
		assertThat(telegram.replies).containsExactly(
				new RecordingFilingPort.Reply(CHAT_ID, 99L, "Already saved → Fitness"));
	}

	@Test
	void needsUserPick_doesNotPersist_asksThemeButtonsOnly() {
		classifier.next = new ClassifierDecision.NeedsUserPick();
		AcceptedDraft draft = new AcceptedDraft(
				CHAT_ID,
				8L,
				INBOX_THREAD,
				Optional.of("https://example.com/ambig"),
				"https://example.com/ambig");

		FilingResult result = filing.file(draft, catalog);

		assertThat(result).isInstanceOf(FilingResult.AwaitingThemePick.class);
		assertThat(savedItemStore.findByCatalogAndUrl(CHAT_ID, "https://example.com/ambig")).isEmpty();
		assertThat(telegram.copies).isEmpty();
		assertThat(telegram.replies).isEmpty();
		assertThat(telegram.buttonReplies).hasSize(1);
		RecordingFilingPort.ButtonReply ask = telegram.buttonReplies.getFirst();
		assertThat(ask.replyToMessageId()).isEqualTo(8L);
		assertThat(ask.text()).isEqualTo(InboxFilingService.PICK_PROMPT);
		assertThat(ask.buttons()).extracting(FilingCallbackButton::label).containsExactly("AI", "Fitness");
		assertThat(ask.buttons()).extracting(FilingCallbackButton::callbackData)
				.containsExactly("f6:8:0", "f6:8:1");
		assertThat(ask.buttons()).noneMatch(b -> b.label().equals("Inbox") || b.label().equals("Smart Search"));
	}

	@Test
	void themePickCallback_completesFilingLikeAutomaticPath() {
		classifier.next = new ClassifierDecision.NeedsUserPick();
		AcceptedDraft draft = new AcceptedDraft(
				CHAT_ID,
				8L,
				INBOX_THREAD,
				Optional.of("https://example.com/pick"),
				"https://example.com/pick");
		filing.file(draft, catalog);
		telegram.buttonReplies.clear();

		FilingResult result = filing.completeThemePick("cb-1", CHAT_ID, 8L, 1, catalog);

		assertThat(result).isInstanceOf(FilingResult.Filed.class);
		assertThat(((FilingResult.Filed) result).themeName()).isEqualTo("Fitness");
		assertThat(savedItemStore.findByCatalogAndUrl(CHAT_ID, "https://example.com/pick"))
				.isPresent()
				.get()
				.extracting(SavedItem::themeName)
				.isEqualTo("Fitness");
		assertThat(telegram.copies).containsExactly(new RecordingFilingPort.Copy(CHAT_ID, 8L, 32L));
		assertThat(telegram.replies).containsExactly(
				new RecordingFilingPort.Reply(CHAT_ID, 8L, "Filed → Fitness"));
		assertThat(telegram.answeredCallbacks).containsExactly("cb-1");
	}

	@Test
	void forwardWithoutUrl_confident_filesChosenTheme() {
		classifier.next = new ClassifierDecision.Confident("AI");
		AcceptedDraft draft = new AcceptedDraft(
				CHAT_ID,
				8L,
				INBOX_THREAD,
				Optional.empty(),
				"Forwarded tip about intervals");

		FilingResult result = filing.file(draft, catalog);

		assertThat(result).isInstanceOf(FilingResult.Filed.class);
		assertThat(((FilingResult.Filed) result).themeName()).isEqualTo("AI");
		assertThat(telegram.copies).containsExactly(new RecordingFilingPort.Copy(CHAT_ID, 8L, 31L));
		assertThat(telegram.replies).containsExactly(
				new RecordingFilingPort.Reply(CHAT_ID, 8L, "Filed → AI"));
	}

	@Test
	void classifier_neverOfferedInboxOrSmartSearchAsThemes() {
		AtomicReference<List<String>> seenThemes = new AtomicReference<>();
		TopicClassifier capturing = (text, themes) -> {
			seenThemes.set(List.copyOf(themes));
			return new ClassifierDecision.Confident(themes.getFirst());
		};
		filing = new InboxFilingService(savedItemStore, telegram, capturing);
		AcceptedDraft draft = new AcceptedDraft(
				CHAT_ID,
				3L,
				INBOX_THREAD,
				Optional.of("https://example.com/y"),
				"https://example.com/y");

		FilingResult result = filing.file(draft, catalog);

		assertThat(seenThemes.get()).containsExactly("AI", "Fitness");
		assertThat(seenThemes.get()).doesNotContain("Inbox", "Smart Search");
		assertThat(((FilingResult.Filed) result).themeName()).isIn("AI", "Fitness");
	}

	private static DataSource dataSource() {
		DriverManagerDataSource ds = new DriverManagerDataSource();
		ds.setUrl(POSTGRES.getJdbcUrl());
		ds.setUsername(POSTGRES.getUsername());
		ds.setPassword(POSTGRES.getPassword());
		ds.setDriverClassName("org.postgresql.Driver");
		return ds;
	}

	private static final class ScriptedClassifier implements TopicClassifier {
		volatile ClassifierDecision next;
		final AtomicInteger calls = new AtomicInteger();

		ScriptedClassifier(ClassifierDecision next) {
			this.next = next;
		}

		@Override
		public ClassifierDecision classify(String draftText, List<String> themeNames) {
			calls.incrementAndGet();
			return next;
		}
	}

	private static final class RecordingFilingPort implements FilingPort {
		final List<Copy> copies = new ArrayList<>();
		final List<Reply> replies = new ArrayList<>();
		final List<ButtonReply> buttonReplies = new ArrayList<>();
		final List<String> answeredCallbacks = new ArrayList<>();
		Optional<Long> nextCopyMessageId = Optional.of(555L);

		@Override
		public Optional<Long> copyMessageToThread(long chatId, long fromMessageId, long messageThreadId) {
			copies.add(new Copy(chatId, fromMessageId, messageThreadId));
			return nextCopyMessageId;
		}

		@Override
		public void replyToMessage(long chatId, long replyToMessageId, String text) {
			replies.add(new Reply(chatId, replyToMessageId, text));
		}

		@Override
		public void replyWithCallbackButtons(
				long chatId, long replyToMessageId, String text, List<FilingCallbackButton> buttons) {
			buttonReplies.add(new ButtonReply(chatId, replyToMessageId, text, List.copyOf(buttons)));
		}

		@Override
		public void answerCallbackQuery(String callbackQueryId) {
			answeredCallbacks.add(callbackQueryId);
		}

		record Copy(long chatId, long fromMessageId, long messageThreadId) {
		}

		record Reply(long chatId, long replyToMessageId, String text) {
		}

		record ButtonReply(long chatId, long replyToMessageId, String text, List<FilingCallbackButton> buttons) {
		}
	}
}
