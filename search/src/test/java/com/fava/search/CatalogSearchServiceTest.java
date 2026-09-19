package com.fava.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fava.catalog.Catalog;
import com.fava.catalog.CatalogSchema;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.CatalogSyncStatus;
import com.fava.catalog.EmbeddingDimensions;
import com.fava.catalog.EmbeddingSpace;
import com.fava.catalog.JdbcCatalogStore;
import com.fava.catalog.JdbcEmbeddingModelRegistry;
import com.fava.catalog.JdbcSavedItemEmbeddingStore;
import com.fava.catalog.JdbcSavedItemStore;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemEmbeddingStore;
import com.fava.catalog.SavedItemStore;
import com.fava.catalog.ThemeTopic;
import com.fava.classify.ChatModelPort;
import com.fava.classify.EmbeddingPort;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
class CatalogSearchServiceTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private static final long CHAT_ID = -100777L;
	private static final long OTHER_CHAT = -100888L;

	private SavedItemStore savedItems;
	private SavedItemEmbeddingStore embeddings;
	private long modelId;
	private RecordingChatModel chat;
	private CatalogSearchService search;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource);
		new JdbcTemplate(dataSource)
				.execute("TRUNCATE saved_item_embeddings, saved_items, theme_topics, catalogs, embedding_models CASCADE");
		CatalogStore catalogs = new JdbcCatalogStore(dataSource);
		savedItems = new JdbcSavedItemStore(dataSource);
		embeddings = new JdbcSavedItemEmbeddingStore(dataSource);
		modelId = new JdbcEmbeddingModelRegistry(dataSource)
				.activate(new EmbeddingSpace("test-model", EmbeddingDimensions.DEFAULT), CatalogSyncStatus.SUCCEEDED)
				.id();
		catalogs.create(new Catalog(CHAT_ID, 1L, 2L, List.of(new ThemeTopic("AI", 3L))));
		catalogs.create(new Catalog(OTHER_CHAT, 1L, 2L, List.of(new ThemeTopic("AI", 3L))));
		chat = new RecordingChatModel("Grounded answer about pilates.");
		search = new CatalogSearchService(
				new FixedEmbeddingPort(ones()),
				embeddings,
				savedItems,
				chat,
				CatalogSearchService.DEFAULT_TOP_K,
				CatalogSearchService.DEFAULT_MAX_DISTANCE);
	}

	@Test
	void emptyCatalog_returnsNothingFound_withoutCallingChatModel() {
		CatalogSearchResult result = search.answer(CHAT_ID, "What did I save about pilates?");

		assertThat(result).isInstanceOf(CatalogSearchResult.NothingFound.class);
		assertThat(((CatalogSearchResult.NothingFound) result).message())
				.isEqualTo(CatalogSearchService.NOTHING_FOUND_MESSAGE);
		assertThat(chat.calls.get()).isZero();
	}

	@Test
	void relevantHit_generatesAnswerWithCitationsFromOnlyThisCatalog() {
		SavedItem mine = savedItems.save(SavedItem.of(
				null,
				CHAT_ID,
				Optional.of("https://example.com/pilates"),
				"Pilates reformer tip for beginners",
				"AI",
				1L));
		SavedItem other = savedItems.save(SavedItem.of(
				null,
				OTHER_CHAT,
				Optional.of("https://other.example/pilates"),
				"Pilates secrets from the open web",
				"AI",
				2L));
		embeddings.upsert(mine.id(), CHAT_ID, ones(), modelId);
		embeddings.upsert(other.id(), OTHER_CHAT, ones(), modelId);

		CatalogSearchResult result = search.answer(CHAT_ID, "Any pilates tips?");

		assertThat(result).isInstanceOf(CatalogSearchResult.Answer.class);
		CatalogSearchResult.Answer answer = (CatalogSearchResult.Answer) result;
		assertThat(answer.text()).isEqualTo("Grounded answer about pilates.");
		assertThat(answer.citations()).hasSize(1);
		assertThat(answer.citations().getFirst().snippet()).contains("Pilates reformer");
		assertThat(answer.citations().getFirst().url()).contains("https://example.com/pilates");
		assertThat(chat.userMessages).hasSize(1);
		assertThat(chat.userMessages.getFirst()).contains("Pilates reformer tip");
		assertThat(chat.userMessages.getFirst()).doesNotContain("open web");
		assertThat(chat.systemPrompts.getFirst()).containsIgnoringCase("only");
	}

	@Test
	void noHitAboveThreshold_returnsNothingFound_withoutCallingChatModel() {
		SavedItem item = savedItems.save(SavedItem.of(
				null, CHAT_ID, Optional.empty(), "unrelated cooking note", "AI", 3L));
		float[] orthogonal = zeros();
		orthogonal[0] = 1f;
		embeddings.upsert(item.id(), CHAT_ID, orthogonal, modelId);

		CatalogSearchResult result = search.answer(CHAT_ID, "pilates?");

		assertThat(result).isInstanceOf(CatalogSearchResult.NothingFound.class);
		assertThat(chat.calls.get()).isZero();
	}

	private static float[] ones() {
		float[] v = new float[EmbeddingDimensions.DEFAULT];
		Arrays.fill(v, 1f / (float) Math.sqrt(v.length));
		return v;
	}

	private static float[] zeros() {
		return new float[EmbeddingDimensions.DEFAULT];
	}

	private static DataSource dataSource() {
		DriverManagerDataSource ds = new DriverManagerDataSource();
		ds.setUrl(POSTGRES.getJdbcUrl());
		ds.setUsername(POSTGRES.getUsername());
		ds.setPassword(POSTGRES.getPassword());
		ds.setDriverClassName("org.postgresql.Driver");
		return ds;
	}

	private static final class FixedEmbeddingPort implements EmbeddingPort {
		private final float[] vector;

		FixedEmbeddingPort(float[] vector) {
			this.vector = vector;
		}

		@Override
		public float[] embed(String text) {
			return vector.clone();
		}
	}

	private static final class RecordingChatModel implements ChatModelPort {
		private final String reply;
		final AtomicInteger calls = new AtomicInteger();
		final List<String> systemPrompts = new ArrayList<>();
		final List<String> userMessages = new ArrayList<>();

		RecordingChatModel(String reply) {
			this.reply = reply;
		}

		@Override
		public String complete(String systemPrompt, String userMessage) {
			calls.incrementAndGet();
			systemPrompts.add(systemPrompt);
			userMessages.add(userMessage);
			return reply;
		}
	}
}
