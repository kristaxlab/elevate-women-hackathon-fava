package com.fava.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class JdbcSavedItemEmbeddingStoreModelTest {

	private static final int DIMS = 8;

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private static final long CHAT = -100123L;

	private SavedItemStore savedItems;
	private SavedItemEmbeddingStore embeddings;
	private EmbeddingModelRegistry registry;
	private EmbeddingModel activeModel;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource, DIMS);
		new JdbcTemplate(dataSource)
				.execute("TRUNCATE saved_item_embeddings, saved_items, theme_topics, catalogs, embedding_models CASCADE");
		CatalogStore catalogs = new JdbcCatalogStore(dataSource);
		savedItems = new JdbcSavedItemStore(dataSource);
		registry = new JdbcEmbeddingModelRegistry(dataSource);
		activeModel = registry.activate(new EmbeddingSpace("test-model", DIMS), CatalogSyncStatus.IN_PROGRESS);
		embeddings = new JdbcSavedItemEmbeddingStore(dataSource, DIMS);
		catalogs.create(new Catalog(CHAT, 11L, 22L, List.of(new ThemeTopic("AI", 31L))));
	}

	@Test
	void upsert_storesEmbeddingModelId_andFindSimilarWorks() {
		SavedItem item = savedItems.save(SavedItem.of(
				null, CHAT, Optional.empty(), "alpha", "AI", 1L));
		float[] vector = ones(DIMS);
		embeddings.upsert(item.id(), CHAT, vector, activeModel.id());

		List<SavedItemHit> hits = embeddings.findSimilar(CHAT, vector, 5, 1.0);
		assertThat(hits).extracting(SavedItemHit::savedItemId).containsExactly(item.id());
	}

	@Test
	void upsert_rejectsWrongDimension() {
		SavedItem item = savedItems.save(SavedItem.of(
				null, CHAT, Optional.empty(), "alpha", "AI", 1L));
		assertThatThrownBy(() -> embeddings.upsert(item.id(), CHAT, ones(3), activeModel.id()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("embedding must have " + DIMS + " dimensions");
	}

	@Test
	void deleteAll_removesEveryEmbedding() {
		SavedItem item = savedItems.save(SavedItem.of(
				null, CHAT, Optional.empty(), "alpha", "AI", 1L));
		embeddings.upsert(item.id(), CHAT, ones(DIMS), activeModel.id());
		embeddings.deleteAll();
		assertThat(embeddings.findSimilar(CHAT, ones(DIMS), 5, 1.0)).isEmpty();
	}

	@Test
	void findNeedingEmbedding_returnsItemsMissingOrWrongModel() {
		EmbeddingModel old = registry.activate(new EmbeddingSpace("old-model", DIMS), CatalogSyncStatus.SUCCEEDED);
		SavedItem missing = savedItems.save(SavedItem.of(
				null, CHAT, Optional.of("https://a.example"), "need embed", "AI", 1L));
		SavedItem current = savedItems.save(SavedItem.of(
				null, CHAT, Optional.of("https://b.example"), "has embed", "AI", 2L));
		SavedItem stale = savedItems.save(SavedItem.of(
				null, CHAT, Optional.of("https://c.example"), "stale embed", "AI", 3L));

		embeddings.upsert(stale.id(), CHAT, ones(DIMS), old.id());
		activeModel = registry.activate(new EmbeddingSpace("test-model", DIMS), CatalogSyncStatus.IN_PROGRESS);
		embeddings.upsert(current.id(), CHAT, ones(DIMS), activeModel.id());

		List<SavedItem> needing = embeddings.findNeedingEmbedding(activeModel.id());
		assertThat(needing).extracting(SavedItem::id).containsExactlyInAnyOrder(missing.id(), stale.id());
	}

	private static float[] ones(int dims) {
		float[] v = new float[dims];
		for (int i = 0; i < dims; i++) {
			v[i] = 1f;
		}
		return v;
	}

	private static DataSource dataSource() {
		DriverManagerDataSource ds = new DriverManagerDataSource();
		ds.setUrl(POSTGRES.getJdbcUrl());
		ds.setUsername(POSTGRES.getUsername());
		ds.setPassword(POSTGRES.getPassword());
		ds.setDriverClassName("org.postgresql.Driver");
		return ds;
	}
}
