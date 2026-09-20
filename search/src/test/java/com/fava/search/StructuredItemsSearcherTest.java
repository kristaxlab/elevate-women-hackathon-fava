package com.fava.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fava.catalog.Catalog;
import com.fava.catalog.CatalogNotFoundException;
import com.fava.catalog.CatalogSchema;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.CatalogSyncStatus;
import com.fava.catalog.EmbeddingDimensions;
import com.fava.catalog.EmbeddingProviderException;
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
import com.fava.classify.EmbeddingPort;
import java.util.Arrays;
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
class StructuredItemsSearcherTest {

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
	private StructuredItemsSearcher searcher;

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
		searcher = newSearcher(new FixedEmbeddingPort(ones()));
	}

	@Test
	void catalogMissing_throwsCatalogNotFound() {
		StructuredQuery query = new StructuredQuery("pilates", 3, StructuredQuery.Filters.NONE);

		assertThatThrownBy(() -> searcher.search(-999L, query, CatalogSearchService.DEFAULT_MAX_DISTANCE))
				.isInstanceOf(CatalogNotFoundException.class);
	}

	@Test
	void zeroHits_emptyCatalog_throwsNoSearchHits() {
		StructuredQuery query = new StructuredQuery("pilates", 3, StructuredQuery.Filters.NONE);

		assertThatThrownBy(() -> searcher.search(CHAT_ID, query, CatalogSearchService.DEFAULT_MAX_DISTANCE))
				.isInstanceOf(NoSearchHitsException.class);
	}

	@Test
	void zeroHits_allAboveCutoff_throwsNoSearchHits() {
		SavedItem item = savedItems.save(SavedItem.of(
				null, CHAT_ID, Optional.empty(), "unrelated cooking note", "AI", 3L));
		float[] orthogonal = zeros();
		orthogonal[0] = 1f;
		embeddings.upsert(item.id(), CHAT_ID, orthogonal, modelId);
		StructuredQuery query = new StructuredQuery("pilates", 3, StructuredQuery.Filters.NONE);

		assertThatThrownBy(() -> searcher.search(CHAT_ID, query, CatalogSearchService.DEFAULT_MAX_DISTANCE))
				.isInstanceOf(NoSearchHitsException.class);
	}

	@Test
	void filterMiss_throwsNoSearchHits_withoutEmbedding() {
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
		searcher = newSearcher(embedding);
		StructuredQuery query = new StructuredQuery(
				"pasta",
				3,
				new StructuredQuery.Filters(
						Optional.empty(),
						Optional.empty(),
						Optional.empty(),
						List.of("mexican"),
						Optional.empty()));

		assertThatThrownBy(() -> searcher.search(CHAT_ID, query, CatalogSearchService.DEFAULT_MAX_DISTANCE))
				.isInstanceOf(NoSearchHitsException.class);
		assertThat(embedding.calls.get()).isZero();
	}

	@Test
	void returnsRankedHitsWithDistance_scopedToCatalog() {
		SavedItem mine = savedItems.save(new SavedItem(
				null,
				CHAT_ID,
				Optional.of("https://example.com/pilates"),
				"Pilates reformer tip for beginners",
				"AI",
				1L,
				Optional.empty(),
				Optional.empty(),
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
		StructuredQuery query = new StructuredQuery("pilates tips", 3, StructuredQuery.Filters.NONE);

		List<RankedSavedItem> hits = searcher.search(CHAT_ID, query, CatalogSearchService.DEFAULT_MAX_DISTANCE);

		assertThat(hits).hasSize(1);
		assertThat(hits.getFirst().item().id()).isEqualTo(mine.id());
		assertThat(hits.getFirst().item().title()).contains("Pilates reformer tip");
		assertThat(hits.getFirst().distance()).isGreaterThanOrEqualTo(0.0);
		assertThat(hits.getFirst().distance()).isLessThanOrEqualTo(CatalogSearchService.DEFAULT_MAX_DISTANCE);
	}

	@Test
	void honorsLimitAndMaxDistance() {
		SavedItem close = savedItems.save(SavedItem.of(
				null, CHAT_ID, Optional.empty(), "close pilates tip", "AI", 20L));
		SavedItem far = savedItems.save(SavedItem.of(
				null, CHAT_ID, Optional.empty(), "far cooking note", "AI", 21L));
		embeddings.upsert(close.id(), CHAT_ID, ones(), modelId);
		float[] orthogonal = zeros();
		orthogonal[0] = 1f;
		embeddings.upsert(far.id(), CHAT_ID, orthogonal, modelId);
		for (int i = 0; i < 3; i++) {
			SavedItem item = savedItems.save(SavedItem.of(
					null, CHAT_ID, Optional.empty(), "extra tip " + i, "AI", 30L + i));
			embeddings.upsert(item.id(), CHAT_ID, ones(), modelId);
		}
		StructuredQuery query = new StructuredQuery("pilates", 2, StructuredQuery.Filters.NONE);

		List<RankedSavedItem> hits = searcher.search(CHAT_ID, query, CatalogSearchService.DEFAULT_MAX_DISTANCE);

		assertThat(hits).hasSize(2);
		assertThat(hits).allMatch(h -> h.distance() <= CatalogSearchService.DEFAULT_MAX_DISTANCE);
		assertThat(hits.stream().map(h -> h.item().id())).doesNotContain(far.id());
	}

	@Test
	void embedFailure_throwsProviderException() {
		SavedItem item = savedItems.save(SavedItem.of(
				null, CHAT_ID, Optional.empty(), "any tip", "AI", 1L));
		embeddings.upsert(item.id(), CHAT_ID, ones(), modelId);
		searcher = newSearcher(text -> {
			throw new IllegalStateException("OpenRouter API key not configured");
		});
		StructuredQuery query = new StructuredQuery("tips", 3, StructuredQuery.Filters.NONE);

		assertThatThrownBy(() -> searcher.search(CHAT_ID, query, CatalogSearchService.DEFAULT_MAX_DISTANCE))
				.isInstanceOf(EmbeddingProviderException.class);
	}

	private StructuredItemsSearcher newSearcher(EmbeddingPort embeddingPort) {
		return new StructuredItemsSearcher(catalogs, savedItems, embeddings, embeddingPort);
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
}
