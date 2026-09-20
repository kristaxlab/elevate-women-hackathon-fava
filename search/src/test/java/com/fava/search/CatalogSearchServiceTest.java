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
import com.fava.catalog.SourceType;
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

	private CatalogStore catalogs;
	private SavedItemStore savedItems;
	private SavedItemEmbeddingStore embeddings;
	private long modelId;
	private RecordingChatModel introChat;
	private CatalogSearchService search;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource);
		new JdbcTemplate(dataSource)
				.execute("TRUNCATE saved_item_embeddings, saved_items, theme_topics, catalogs, embedding_models CASCADE");
		catalogs = new JdbcCatalogStore(dataSource);
		savedItems = new JdbcSavedItemStore(dataSource);
		embeddings = new JdbcSavedItemEmbeddingStore(dataSource);
		modelId = new JdbcEmbeddingModelRegistry(dataSource)
				.activate(new EmbeddingSpace("test-model", EmbeddingDimensions.DEFAULT), CatalogSyncStatus.SUCCEEDED)
				.id();
		catalogs.create(new Catalog(CHAT_ID, 1L, 2L, List.of(new ThemeTopic("AI", 3L), new ThemeTopic("Food", 4L))));
		catalogs.create(new Catalog(OTHER_CHAT, 1L, 2L, List.of(new ThemeTopic("AI", 3L))));
		introChat = new RecordingChatModel("Here are matching saves from your Catalog.");
		search = newSearch("""
				{"query":"pilates tips","limit":3,"filters":{}}
				""");
	}

	@Test
	void emptyCatalog_returnsNothingFound_withoutCallingIntro() {
		CatalogSearchResult result = search.answer(CHAT_ID, "What did I save about pilates?");

		assertThat(result).isInstanceOf(CatalogSearchResult.NothingFound.class);
		assertThat(((CatalogSearchResult.NothingFound) result).message())
				.isEqualTo(CatalogSearchService.NOTHING_FOUND_MESSAGE);
		assertThat(introChat.calls.get()).isZero();
	}

	@Test
	void relevantHit_returnsRankedListWithGroundedIntroAndDeepLink() {
		SavedItem mine = savedItems.save(new SavedItem(
				null,
				CHAT_ID,
				Optional.of("https://example.com/pilates"),
				"Pilates reformer tip for beginners",
				"AI",
				1L,
				Optional.of(SavedItem.USER_LIB_TYPE_TELEGRAM),
				Optional.of("9001"),
				Optional.of(SourceType.ARTICLE),
				Optional.of("Pilates reformer tip"),
				Optional.empty(),
				List.of("fitness"),
				Optional.of("Pilates reformer tip for beginners")));
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
		assertThat(answer.intro()).isEqualTo("Here are matching saves from your Catalog.");
		assertThat(answer.items()).hasSize(1);
		assertThat(answer.items().getFirst().title()).isEqualTo("Pilates reformer tip");
		assertThat(answer.items().getFirst().sourceType()).contains("article");
		assertThat(answer.items().getFirst().themeTopicLink())
				.contains("https://t.me/c/777/9001");
		assertThat(introChat.userMessages.getFirst()).contains("Pilates reformer tip");
		assertThat(introChat.userMessages.getFirst()).doesNotContain("open web");
		assertThat(introChat.systemPrompts.getFirst()).containsIgnoringCase("ONLY");
	}

	@Test
	void noHitAboveThreshold_returnsNothingFound_withoutCallingIntro() {
		SavedItem item = savedItems.save(SavedItem.of(
				null, CHAT_ID, Optional.empty(), "unrelated cooking note", "AI", 3L));
		float[] orthogonal = zeros();
		orthogonal[0] = 1f;
		embeddings.upsert(item.id(), CHAT_ID, orthogonal, modelId);

		CatalogSearchResult result = search.answer(CHAT_ID, "pilates?");

		assertThat(result).isInstanceOf(CatalogSearchResult.NothingFound.class);
		assertThat(introChat.calls.get()).isZero();
	}

	@Test
	void explicitFiltersMatchNothing_hardEmpty_withoutSemanticOrIntro() {
		SavedItem item = savedItems.save(new SavedItem(
				null,
				CHAT_ID,
				Optional.empty(),
				"pasta recipe",
				"Food",
				4L,
				Optional.empty(),
				Optional.empty(),
				Optional.of(SourceType.RECIPE),
				Optional.of("Pasta"),
				Optional.of("Anna"),
				List.of("italian"),
				Optional.of("Pasta")));
		embeddings.upsert(item.id(), CHAT_ID, ones(), modelId);
		RecordingEmbeddingPort embedding = new RecordingEmbeddingPort(ones());
		search = new CatalogSearchService(
				new StructuredQueryParser((system, user) -> """
						{"query":"pasta","limit":3,"filters":{"tags":["mexican"]}}
						"""),
				new StructuredItemsSearcher(catalogs, savedItems, embeddings, embedding),
				introChat,
				CatalogSearchService.DEFAULT_MAX_DISTANCE);

		CatalogSearchResult result = search.answer(CHAT_ID, "mexican pasta?");

		assertThat(result).isInstanceOf(CatalogSearchResult.NothingFound.class);
		assertThat(embedding.calls.get()).isZero();
		assertThat(introChat.calls.get()).isZero();
	}

	@Test
	void filtersThenRanks_onlyMatchingCandidates() {
		SavedItem pasta = savedItems.save(new SavedItem(
				null,
				CHAT_ID,
				Optional.empty(),
				"pasta dinner",
				"Food",
				5L,
				Optional.empty(),
				Optional.empty(),
				Optional.of(SourceType.RECIPE),
				Optional.of("Pasta dinner"),
				Optional.of("Anna"),
				List.of("italian", "dinner"),
				Optional.of("Pasta dinner")));
		SavedItem salad = savedItems.save(new SavedItem(
				null,
				CHAT_ID,
				Optional.empty(),
				"green salad",
				"Food",
				6L,
				Optional.empty(),
				Optional.empty(),
				Optional.of(SourceType.RECIPE),
				Optional.of("Green salad"),
				Optional.of("Bob"),
				List.of("salad"),
				Optional.of("Green salad")));
		embeddings.upsert(pasta.id(), CHAT_ID, ones(), modelId);
		embeddings.upsert(salad.id(), CHAT_ID, ones(), modelId);
		search = newSearch("""
				{"query":"dinner","limit":3,"filters":{"recommended_by":"Anna","tags":["italian","dinner"]}}
				""");

		CatalogSearchResult result = search.answer(CHAT_ID, "Anna italian dinner?");

		assertThat(result).isInstanceOf(CatalogSearchResult.Answer.class);
		CatalogSearchResult.Answer answer = (CatalogSearchResult.Answer) result;
		assertThat(answer.items()).hasSize(1);
		assertThat(answer.items().getFirst().title()).isEqualTo("Pasta dinner");
	}

	@Test
	void userStatedLimit_isHonoredUpToTen() {
		for (int i = 0; i < 5; i++) {
			SavedItem item = savedItems.save(SavedItem.of(
					null, CHAT_ID, Optional.empty(), "tip number " + i, "AI", 10L + i));
			embeddings.upsert(item.id(), CHAT_ID, ones(), modelId);
		}
		search = newSearch("""
				{"query":"tips","limit":2,"filters":{}}
				""");

		CatalogSearchResult result = search.answer(CHAT_ID, "give me 2 tips");

		assertThat(result).isInstanceOf(CatalogSearchResult.Answer.class);
		assertThat(((CatalogSearchResult.Answer) result).items()).hasSize(2);
	}

	@Test
	void distanceCutoff_mayReturnFewerThanLimit() {
		SavedItem close = savedItems.save(SavedItem.of(
				null, CHAT_ID, Optional.empty(), "close pilates tip", "AI", 20L));
		SavedItem far = savedItems.save(SavedItem.of(
				null, CHAT_ID, Optional.empty(), "far cooking note", "AI", 21L));
		embeddings.upsert(close.id(), CHAT_ID, ones(), modelId);
		float[] orthogonal = zeros();
		orthogonal[0] = 1f;
		embeddings.upsert(far.id(), CHAT_ID, orthogonal, modelId);
		search = newSearch("""
				{"query":"pilates","limit":3,"filters":{}}
				""");

		CatalogSearchResult result = search.answer(CHAT_ID, "pilates?");

		assertThat(result).isInstanceOf(CatalogSearchResult.Answer.class);
		assertThat(((CatalogSearchResult.Answer) result).items()).hasSize(1);
		assertThat(((CatalogSearchResult.Answer) result).items().getFirst().title())
				.contains("close pilates");
	}

	private CatalogSearchService newSearch(String structuredJson) {
		return new CatalogSearchService(
				new StructuredQueryParser((system, user) -> structuredJson),
				new StructuredItemsSearcher(catalogs, savedItems, embeddings, new FixedEmbeddingPort(ones())),
				introChat,
				CatalogSearchService.DEFAULT_MAX_DISTANCE);
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

	private static final class RecordingEmbeddingPort implements EmbeddingPort {
		private final float[] vector;
		final AtomicInteger calls = new AtomicInteger();

		RecordingEmbeddingPort(float[] vector) {
			this.vector = vector;
		}

		@Override
		public float[] embed(String text) {
			calls.incrementAndGet();
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
