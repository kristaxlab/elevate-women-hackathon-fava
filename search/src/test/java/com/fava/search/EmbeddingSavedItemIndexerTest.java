package com.fava.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fava.catalog.Catalog;
import com.fava.catalog.CatalogSchema;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.CatalogSyncStatus;
import com.fava.catalog.EmbeddingDimensions;
import com.fava.catalog.EmbeddingModelRegistry;
import com.fava.catalog.EmbeddingSpace;
import com.fava.catalog.JdbcCatalogStore;
import com.fava.catalog.JdbcEmbeddingModelRegistry;
import com.fava.catalog.JdbcSavedItemEmbeddingStore;
import com.fava.catalog.JdbcSavedItemStore;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemEmbeddingStore;
import com.fava.catalog.SavedItemHit;
import com.fava.catalog.SavedItemStore;
import com.fava.catalog.ThemeTopic;
import com.fava.classify.EmbeddingPort;
import java.util.Arrays;
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
class EmbeddingSavedItemIndexerTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private static final long CHAT_ID = -100321L;

	private SavedItemStore savedItems;
	private SavedItemEmbeddingStore embeddings;
	private EmbeddingSavedItemIndexer indexer;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource);
		new JdbcTemplate(dataSource)
				.execute("TRUNCATE saved_item_embeddings, saved_items, theme_topics, catalogs, embedding_models CASCADE");
		CatalogStore catalogs = new JdbcCatalogStore(dataSource);
		savedItems = new JdbcSavedItemStore(dataSource);
		embeddings = new JdbcSavedItemEmbeddingStore(dataSource);
		EmbeddingModelRegistry registry = new JdbcEmbeddingModelRegistry(dataSource);
		registry.activate(new EmbeddingSpace("test-model", EmbeddingDimensions.DEFAULT), CatalogSyncStatus.SUCCEEDED);
		catalogs.create(new Catalog(CHAT_ID, 1L, 2L, List.of(new ThemeTopic("AI", 3L))));
		indexer = new EmbeddingSavedItemIndexer(
				new FixedEmbeddingPort(ones()), embeddings, registry, EmbeddingDimensions.DEFAULT);
	}

	@Test
	void index_embedsBodyAndUrl_andUpsertsRetrievableVector() {
		SavedItem saved = savedItems.save(SavedItem.of(
				null,
				CHAT_ID,
				Optional.of("https://example.com/post"),
				"useful tip",
				"AI",
				9L));

		indexer.index(saved);

		List<SavedItemHit> hits = embeddings.findSimilar(CHAT_ID, ones(), 3, 0.1);
		assertThat(hits).extracting(SavedItemHit::savedItemId).containsExactly(saved.id());
	}

	private static float[] ones() {
		float[] v = new float[EmbeddingDimensions.DEFAULT];
		Arrays.fill(v, 1f / (float) Math.sqrt(v.length));
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
}
