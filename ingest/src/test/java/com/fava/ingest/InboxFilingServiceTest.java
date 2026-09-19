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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
		filing = new InboxFilingService(savedItemStore, telegram);
	}

	@Test
	void filesNewUrlIntoFirstTheme_persists_copiesAndReplies() {
		AcceptedDraft draft = new AcceptedDraft(
				CHAT_ID,
				7L,
				INBOX_THREAD,
				Optional.of("https://www.instagram.com/p/NEW/"),
				"Check this https://www.instagram.com/p/NEW/");

		FilingResult result = filing.file(draft, catalog);

		assertThat(result).isInstanceOf(FilingResult.Filed.class);
		assertThat(((FilingResult.Filed) result).themeName()).isEqualTo("AI");
		assertThat(savedItemStore.findByCatalogAndUrl(CHAT_ID, "https://www.instagram.com/p/NEW/"))
				.isPresent()
				.get()
				.extracting(SavedItem::themeName)
				.isEqualTo("AI");
		assertThat(telegram.copies).containsExactly(new RecordingFilingPort.Copy(CHAT_ID, 7L, 31L));
		assertThat(telegram.replies).containsExactly(
				new RecordingFilingPort.Reply(CHAT_ID, 7L, "Filed → AI"));
	}

	@Test
	void duplicateUrlInSameCatalog_skipsCopyAndReportsExistingTheme() {
		savedItemStore.save(new SavedItem(
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
		assertThat(telegram.copies).isEmpty();
		assertThat(telegram.replies).containsExactly(
				new RecordingFilingPort.Reply(CHAT_ID, 99L, "Already saved → Fitness"));
	}

	@Test
	void forwardWithoutUrl_filesIntoFirstTheme() {
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
	void stubClassifier_neverPicksInboxOrSmartSearch() {
		AcceptedDraft draft = new AcceptedDraft(
				CHAT_ID,
				3L,
				INBOX_THREAD,
				Optional.of("https://example.com/y"),
				"https://example.com/y");

		FilingResult result = filing.file(draft, catalog);

		assertThat(((FilingResult.Filed) result).themeName()).isIn("AI", "Fitness");
		assertThat(((FilingResult.Filed) result).themeName()).isNotEqualTo("Inbox");
		assertThat(((FilingResult.Filed) result).themeName()).isNotEqualTo("Smart Search");
	}

	private static DataSource dataSource() {
		DriverManagerDataSource ds = new DriverManagerDataSource();
		ds.setUrl(POSTGRES.getJdbcUrl());
		ds.setUsername(POSTGRES.getUsername());
		ds.setPassword(POSTGRES.getPassword());
		ds.setDriverClassName("org.postgresql.Driver");
		return ds;
	}

	private static final class RecordingFilingPort implements FilingPort {
		final List<Copy> copies = new ArrayList<>();
		final List<Reply> replies = new ArrayList<>();

		@Override
		public void copyMessageToThread(long chatId, long fromMessageId, long messageThreadId) {
			copies.add(new Copy(chatId, fromMessageId, messageThreadId));
		}

		@Override
		public void replyToMessage(long chatId, long replyToMessageId, String text) {
			replies.add(new Reply(chatId, replyToMessageId, text));
		}

		record Copy(long chatId, long fromMessageId, long messageThreadId) {
		}

		record Reply(long chatId, long replyToMessageId, String text) {
		}
	}
}
