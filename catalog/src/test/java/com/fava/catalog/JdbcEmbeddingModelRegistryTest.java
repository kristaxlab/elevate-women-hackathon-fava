package com.fava.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class JdbcEmbeddingModelRegistryTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private EmbeddingModelRegistry registry;
	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource, 8);
		jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("TRUNCATE saved_item_embeddings, saved_items, theme_topics, catalogs, embedding_models CASCADE");
		registry = new JdbcEmbeddingModelRegistry(dataSource);
	}

	@Test
	void findActive_whenEmpty_returnsEmpty() {
		assertThat(registry.findActive()).isEmpty();
	}

	@Test
	void activate_insertsSoleActiveModel() {
		EmbeddingModel activated = registry.activate("openai/text-embedding-3-small", 1536, CatalogSyncStatus.PENDING);

		Optional<EmbeddingModel> active = registry.findActive();
		assertThat(active).isPresent();
		assertThat(active.get().id()).isEqualTo(activated.id());
		assertThat(active.get().modelId()).isEqualTo("openai/text-embedding-3-small");
		assertThat(active.get().dimensions()).isEqualTo(1536);
		assertThat(active.get().active()).isTrue();
		assertThat(active.get().catalogSyncStatus()).isEqualTo(CatalogSyncStatus.PENDING);
	}

	@Test
	void activate_deactivatesPreviousActiveModel() {
		EmbeddingModel first = registry.activate("model-a", 8, CatalogSyncStatus.SUCCEEDED);
		EmbeddingModel second = registry.activate("model-b", 16, CatalogSyncStatus.IN_PROGRESS);

		assertThat(registry.findActive()).hasValueSatisfying(m -> {
			assertThat(m.id()).isEqualTo(second.id());
			assertThat(m.modelId()).isEqualTo("model-b");
			assertThat(m.dimensions()).isEqualTo(16);
		});
		Integer firstActive = jdbc.queryForObject(
				"SELECT CAST(is_active AS INT) FROM embedding_models WHERE id = ?", Integer.class, first.id());
		assertThat(firstActive).isZero();
	}

	@Test
	void updateSyncStatus_changesActiveModelStatus() {
		EmbeddingModel model = registry.activate("model-a", 8, CatalogSyncStatus.IN_PROGRESS);
		registry.updateSyncStatus(model.id(), CatalogSyncStatus.FAILED);

		assertThat(registry.findActive()).hasValueSatisfying(m ->
				assertThat(m.catalogSyncStatus()).isEqualTo(CatalogSyncStatus.FAILED));
	}

	@Test
	void onlyOneActive_enforcedByDatabase() {
		registry.activate("model-a", 8, CatalogSyncStatus.SUCCEEDED);
		assertThatThrownBy(() -> jdbc.update(
						"""
								INSERT INTO embedding_models (model_id, dimensions, is_active, catalog_sync_status)
								VALUES ('model-b', 8, TRUE, 'PENDING')
								"""))
				.hasMessageContaining("embedding_models_one_active");
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
