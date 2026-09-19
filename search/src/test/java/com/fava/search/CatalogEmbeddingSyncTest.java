package com.fava.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fava.catalog.Catalog;
import com.fava.catalog.CatalogSchema;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.CatalogSyncStatus;
import com.fava.catalog.EmbeddingModel;
import com.fava.catalog.EmbeddingModelRegistry;
import com.fava.catalog.EmbeddingSpace;
import com.fava.catalog.JdbcCatalogStore;
import com.fava.catalog.JdbcEmbeddingModelRegistry;
import com.fava.catalog.JdbcSavedItemEmbeddingStore;
import com.fava.catalog.JdbcSavedItemStore;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemEmbeddingStore;
import com.fava.catalog.SavedItemStore;
import com.fava.catalog.ThemeTopic;
import com.fava.classify.EmbeddingPort;
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
class CatalogEmbeddingSyncTest {

	private static final int DIMS = 8;
	private static final EmbeddingSpace SPACE_A = new EmbeddingSpace("model-a", DIMS);
	private static final EmbeddingSpace SPACE_B = new EmbeddingSpace("model-b", DIMS);
	private static final long CHAT = -100123L;

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private DataSource dataSource;
	private EmbeddingModelRegistry registry;
	private SavedItemStore savedItems;
	private SavedItemEmbeddingStore embeddings;
	private CatalogEmbeddingSync sync;
	private FixedEmbeddingPort embeddingPort;

	@BeforeEach
	void setUp() {
		dataSource = dataSource();
		CatalogSchema.ensure(dataSource, SPACE_A);
		new JdbcTemplate(dataSource)
				.execute("TRUNCATE saved_item_embeddings, saved_items, theme_topics, catalogs, embedding_models CASCADE");
		registry = new JdbcEmbeddingModelRegistry(dataSource);
		savedItems = new JdbcSavedItemStore(dataSource);
		embeddings = new JdbcSavedItemEmbeddingStore(dataSource, SPACE_A);
		embeddingPort = new FixedEmbeddingPort(ones(DIMS));
		CatalogStore catalogs = new JdbcCatalogStore(dataSource);
		catalogs.create(new Catalog(CHAT, 11L, 22L, List.of(new ThemeTopic("AI", 31L))));
		sync = new CatalogEmbeddingSync(dataSource, registry, embeddings, embeddingPort, SPACE_A);
	}

	@Test
	void firstBoot_seedsRegistryAndEmbedsSavedItems() {
		SavedItem item = savedItems.save(new SavedItem(
				null, CHAT, Optional.of("https://a.example"), "alpha body", "AI", 1L));

		sync.ensureSynced(SPACE_A);

		EmbeddingModel active = registry.findActive().orElseThrow();
		assertThat(active.modelId()).isEqualTo("model-a");
		assertThat(active.dimensions()).isEqualTo(DIMS);
		assertThat(active.catalogSyncStatus()).isEqualTo(CatalogSyncStatus.SUCCEEDED);
		assertThat(embeddings.findNeedingEmbedding(active.id())).isEmpty();
		assertThat(embeddings.findSimilar(CHAT, ones(DIMS), 5, 1.0))
				.extracting(h -> h.savedItemId())
				.containsExactly(item.id());
	}

	@Test
	void modelChange_wipesAndReembedsWithNewModel() {
		sync.ensureSynced(SPACE_A);
		SavedItem item = savedItems.save(new SavedItem(
				null, CHAT, Optional.empty(), "body", "AI", 1L));
		new EmbeddingSavedItemIndexer(embeddingPort, embeddings, registry, DIMS).index(item);
		assertThat(embeddings.findNeedingEmbedding(registry.findActive().orElseThrow().id())).isEmpty();

		FixedEmbeddingPort other = new FixedEmbeddingPort(twos(DIMS));
		CatalogEmbeddingSync resync = new CatalogEmbeddingSync(dataSource, registry, embeddings, other, SPACE_B);
		resync.ensureSynced(SPACE_B);

		EmbeddingModel active = registry.findActive().orElseThrow();
		assertThat(active.modelId()).isEqualTo("model-b");
		assertThat(active.catalogSyncStatus()).isEqualTo(CatalogSyncStatus.SUCCEEDED);
		assertThat(embeddings.findNeedingEmbedding(active.id())).isEmpty();
		assertThat(other.embedCalls.get()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void syncFailure_marksFailedAndAborts_thenResumeFinishesRemaining() {
		SavedItem first = savedItems.save(new SavedItem(
				null, CHAT, Optional.of("https://1.example"), "one", "AI", 1L));
		SavedItem second = savedItems.save(new SavedItem(
				null, CHAT, Optional.of("https://2.example"), "two", "AI", 2L));

		FailAfterOnePort flaky = new FailAfterOnePort(ones(DIMS));
		CatalogEmbeddingSync failing = new CatalogEmbeddingSync(dataSource, registry, embeddings, flaky, SPACE_A);
		assertThatThrownBy(() -> failing.ensureSynced(SPACE_A))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Catalog embedding sync failed");

		EmbeddingModel active = registry.findActive().orElseThrow();
		assertThat(active.catalogSyncStatus()).isEqualTo(CatalogSyncStatus.FAILED);
		assertThat(embeddings.findNeedingEmbedding(active.id())).hasSize(1);

		CatalogEmbeddingSync resume = new CatalogEmbeddingSync(
				dataSource, registry, embeddings, new FixedEmbeddingPort(ones(DIMS)), SPACE_A);
		resume.ensureSynced(SPACE_A);

		active = registry.findActive().orElseThrow();
		assertThat(active.catalogSyncStatus()).isEqualTo(CatalogSyncStatus.SUCCEEDED);
		assertThat(embeddings.findNeedingEmbedding(active.id())).isEmpty();
		assertThat(embeddings.findSimilar(CHAT, ones(DIMS), 5, 1.0))
				.extracting(h -> h.savedItemId())
				.containsExactlyInAnyOrder(first.id(), second.id());
	}

	@Test
	void dimensionChange_recreatesTableAndSucceeds() {
		sync.ensureSynced(SPACE_A);
		SavedItem item = savedItems.save(new SavedItem(
				null, CHAT, Optional.empty(), "body", "AI", 1L));
		new EmbeddingSavedItemIndexer(embeddingPort, embeddings, registry, DIMS).index(item);

		EmbeddingSpace newSpace = new EmbeddingSpace("model-a", 4);
		SavedItemEmbeddingStore newStore = new JdbcSavedItemEmbeddingStore(dataSource, newSpace);
		CatalogEmbeddingSync dimSync = new CatalogEmbeddingSync(
				dataSource, registry, newStore, new FixedEmbeddingPort(ones(4)), newSpace);
		dimSync.ensureSynced(newSpace);

		EmbeddingModel active = registry.findActive().orElseThrow();
		assertThat(active.dimensions()).isEqualTo(4);
		assertThat(active.catalogSyncStatus()).isEqualTo(CatalogSyncStatus.SUCCEEDED);
		assertThat(newStore.findNeedingEmbedding(active.id())).isEmpty();
	}

	private static float[] ones(int dims) {
		float[] v = new float[dims];
		for (int i = 0; i < dims; i++) {
			v[i] = 1f;
		}
		return v;
	}

	private static float[] twos(int dims) {
		float[] v = new float[dims];
		for (int i = 0; i < dims; i++) {
			v[i] = 2f;
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

	private static final class FixedEmbeddingPort implements EmbeddingPort {
		private final float[] vector;
		private final AtomicInteger embedCalls = new AtomicInteger();

		FixedEmbeddingPort(float[] vector) {
			this.vector = vector;
		}

		@Override
		public float[] embed(String text) {
			embedCalls.incrementAndGet();
			return vector.clone();
		}
	}

	private static final class FailAfterOnePort implements EmbeddingPort {
		private final float[] vector;
		private int calls;

		FailAfterOnePort(float[] vector) {
			this.vector = vector;
		}

		@Override
		public float[] embed(String text) {
			calls++;
			if (calls > 1) {
				throw new RuntimeException("boom");
			}
			return vector.clone();
		}
	}
}
