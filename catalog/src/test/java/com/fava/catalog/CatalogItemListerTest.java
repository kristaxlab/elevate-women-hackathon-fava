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
class CatalogItemListerTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private static final long CHAT_ID = -100123L;
	private static final float[] VECTOR = pad(new float[] {1f, 0f, 0f});

	private CatalogStore catalogs;
	private SavedItemStore savedItems;
	private SavedItemEmbeddingStore embeddings;
	private EmbeddingModelRegistry models;
	private CatalogItemLister lister;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource);
		new JdbcTemplate(dataSource)
				.execute("TRUNCATE saved_item_embeddings, saved_items, theme_topics, catalogs, embedding_models CASCADE");
		catalogs = new JdbcCatalogStore(dataSource);
		savedItems = new JdbcSavedItemStore(dataSource);
		embeddings = new JdbcSavedItemEmbeddingStore(dataSource);
		models = new JdbcEmbeddingModelRegistry(dataSource);
		models.activate(new EmbeddingSpace("test-model", EmbeddingDimensions.DEFAULT), CatalogSyncStatus.SUCCEEDED);
		catalogs.create(new Catalog(
				CHAT_ID, 11L, 22L, List.of(new ThemeTopic("AI", 31L), new ThemeTopic("Fitness", 32L))));
		lister = new CatalogItemLister(catalogs, savedItems, embeddings);
	}

	@Test
	void list_whenCatalogMissing_throws() {
		assertThatThrownBy(() -> lister.list(-999L)).isInstanceOf(CatalogNotFoundException.class);
	}

	@Test
	void list_whenCatalogEmpty_returnsEmptyList() {
		assertThat(lister.list(CHAT_ID)).isEmpty();
	}

	@Test
	void list_returnsItemsWithOptionalEmbeddings() {
		SavedItem withEmbed = savedItems.save(item("https://example.com/a", "AI", "with vector"));
		SavedItem withoutEmbed = savedItems.save(item("https://example.com/b", "Fitness", "no vector"));
		EmbeddingModel active = models.findActive().orElseThrow();
		embeddings.upsert(withEmbed.id(), CHAT_ID, VECTOR.clone(), active.id());

		List<ListedSavedItem> listed = lister.list(CHAT_ID);

		assertThat(listed).hasSize(2);
		ListedSavedItem first = listed.stream()
				.filter(row -> row.item().id().equals(withEmbed.id()))
				.findFirst()
				.orElseThrow();
		ListedSavedItem second = listed.stream()
				.filter(row -> row.item().id().equals(withoutEmbed.id()))
				.findFirst()
				.orElseThrow();
		assertThat(first.embedding()).isPresent();
		assertThat(first.embedding().get().modelId()).isEqualTo("test-model");
		assertThat(first.embedding().get().vector()).isEqualTo(VECTOR);
		assertThat(second.embedding()).isEmpty();
	}

	private static SavedItem item(String url, String theme, String body) {
		return new SavedItem(
				null,
				CHAT_ID,
				Optional.of(url),
				body,
				theme,
				0L,
				Optional.empty(),
				Optional.empty(),
				Optional.of(SourceType.ARTICLE),
				Optional.of("Title"),
				Optional.empty(),
				List.of(),
				Optional.of(body));
	}

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
