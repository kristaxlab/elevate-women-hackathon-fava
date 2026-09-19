package com.fava.catalog;

import static org.assertj.core.api.Assertions.assertThat;

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
class JdbcSavedItemEmbeddingStoreTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private static final long CHAT_A = -100123L;
	private static final long CHAT_B = -100999L;

	private SavedItemStore savedItems;
	private SavedItemEmbeddingStore embeddings;
	private long modelId;

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
		catalogs.create(new Catalog(CHAT_A, 11L, 22L, List.of(new ThemeTopic("AI", 31L))));
		catalogs.create(new Catalog(CHAT_B, 11L, 22L, List.of(new ThemeTopic("AI", 31L))));
	}

	@Test
	void upsert_thenFindSimilar_returnsItemForSameCatalogOnly() {
		SavedItem itemA = savedItems.save(SavedItem.of(
				null, CHAT_A, Optional.of("https://a.example/1"), "alpha tip", "AI", 1L));
		SavedItem itemB = savedItems.save(SavedItem.of(
				null, CHAT_B, Optional.of("https://b.example/1"), "alpha tip other catalog", "AI", 2L));

		float[] vector = unitVector(1f, 0f, 0f);
		embeddings.upsert(itemA.id(), CHAT_A, pad(vector), modelId);
		embeddings.upsert(itemB.id(), CHAT_B, pad(vector), modelId);

		List<SavedItemHit> hits = embeddings.findSimilar(CHAT_A, pad(vector), 5, 1.0);

		assertThat(hits).extracting(SavedItemHit::savedItemId).containsExactly(itemA.id());
		assertThat(hits.getFirst().distance()).isLessThan(0.01);
	}

	@Test
	void findSimilar_excludesHitsAboveDistanceThreshold() {
		SavedItem item = savedItems.save(SavedItem.of(
				null, CHAT_A, Optional.empty(), "orthogonal", "AI", 3L));
		embeddings.upsert(item.id(), CHAT_A, pad(unitVector(0f, 1f, 0f)), modelId);

		List<SavedItemHit> hits = embeddings.findSimilar(CHAT_A, pad(unitVector(1f, 0f, 0f)), 5, 0.2);

		assertThat(hits).isEmpty();
	}

	private static float[] unitVector(float x, float y, float z) {
		return new float[] {x, y, z};
	}

	/** Pads a short unit vector into {@link EmbeddingDimensions#DEFAULT} dims. */
	private static float[] pad(float[] head) {
		float[] full = new float[EmbeddingDimensions.DEFAULT];
		System.arraycopy(head, 0, full, 0, head.length);
		return full;
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
