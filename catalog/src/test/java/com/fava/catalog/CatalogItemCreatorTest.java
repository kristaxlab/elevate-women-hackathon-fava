package com.fava.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fava.classify.EmbeddingPort;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CatalogItemCreatorTest {

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
	private CatalogItemCreator creator;
	private AtomicInteger embedCalls;

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
		embedCalls = new AtomicInteger();
		EmbeddingPort embeddingPort = text -> {
			embedCalls.incrementAndGet();
			return VECTOR.clone();
		};
		creator = new CatalogItemCreator(
				catalogs,
				savedItems,
				embeddings,
				models,
				embeddingPort,
				new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
	}

	@Test
	void create_persistsItemAndEmbedding() {
		SavedItem request = enriched(null, Optional.of("https://example.com/new"), "AI", "body about ai");

		SavedItemCreateResult.Created created = (SavedItemCreateResult.Created) creator.create(request);

		assertThat(created.item().id()).isNotNull();
		assertThat(created.item().themeName()).isEqualTo("AI");
		assertThat(created.item().sourceMessageId()).isZero();
		assertThat(created.embedding().modelId()).isEqualTo("test-model");
		assertThat(created.embedding().vector()).isEqualTo(VECTOR);
		assertThat(savedItems.findById(created.item().id())).isPresent();
		assertThat(embeddings.findBySavedItemId(created.item().id())).isPresent();
		assertThat(embedCalls).hasValue(1);
	}

	@Test
	void create_rejectsNonNullId() {
		SavedItem request = enriched(42L, Optional.of("https://example.com/x"), "AI", "body");

		assertThatThrownBy(() -> creator.create(request))
				.isInstanceOf(InvalidSavedItemCreateException.class)
				.hasMessageContaining("id");
		assertThat(embedCalls).hasValue(0);
	}

	@Test
	void create_whenCatalogMissing_throws() {
		SavedItem request = enriched(null, Optional.of("https://example.com/x"), "AI", "body");
		SavedItem otherChat = new SavedItem(
				null,
				-999L,
				request.url(),
				request.bodyText(),
				request.themeName(),
				0L,
				Optional.empty(),
				Optional.empty(),
				request.sourceType(),
				request.title(),
				request.recommendedBy(),
				request.tags(),
				request.searchText());

		assertThatThrownBy(() -> creator.create(otherChat)).isInstanceOf(CatalogNotFoundException.class);
	}

	@Test
	void create_whenThemeUnknown_throws() {
		SavedItem request = enriched(null, Optional.of("https://example.com/x"), "Unknown", "body");

		assertThatThrownBy(() -> creator.create(request)).isInstanceOf(ThemeNotFoundException.class);
	}

	@Test
	void create_duplicateUrl_returnsExistingWithoutReembed() {
		SavedItem first = enriched(null, Optional.of("https://example.com/dup"), "AI", "first");
		SavedItemCreateResult.Created created = (SavedItemCreateResult.Created) creator.create(first);
		embedCalls.set(0);

		SavedItemCreateResult result = creator.create(enriched(null, Optional.of("https://example.com/dup"), "Fitness", "second"));

		assertThat(result).isInstanceOf(SavedItemCreateResult.Duplicate.class);
		SavedItemCreateResult.Duplicate dup = (SavedItemCreateResult.Duplicate) result;
		assertThat(dup.item().id()).isEqualTo(created.item().id());
		assertThat(dup.item().themeName()).isEqualTo("AI");
		assertThat(dup.embedding()).contains(created.embedding());
		assertThat(embedCalls).hasValue(0);
	}

	@Test
	void create_withoutUrl_alwaysInserts() {
		creator.create(enriched(null, Optional.empty(), "AI", "note one"));
		SavedItemCreateResult second = creator.create(enriched(null, Optional.empty(), "Fitness", "note two"));

		assertThat(second).isInstanceOf(SavedItemCreateResult.Created.class);
		assertThat(savedItems.findByCatalogFilters(CHAT_ID, SavedItemFilters.NONE)).hasSize(2);
	}

	@Test
	void create_whenEmbedFails_persistsNothing() {
		EmbeddingPort failing = text -> {
			throw new IllegalStateException("OpenRouter down");
		};
		CatalogItemCreator failingCreator = new CatalogItemCreator(
				catalogs,
				savedItems,
				embeddings,
				models,
				failing,
				new TransactionTemplate(new DataSourceTransactionManager(dataSource())));

		assertThatThrownBy(() -> failingCreator.create(
						enriched(null, Optional.of("https://example.com/fail"), "AI", "body")))
				.isInstanceOf(EmbeddingProviderException.class);

		assertThat(savedItems.findByCatalogFilters(CHAT_ID, SavedItemFilters.NONE)).isEmpty();
	}

	@Test
	void create_defaultsSourceMessageIdAndUserLibWhenEmpty() {
		SavedItemCreateResult.Created created = (SavedItemCreateResult.Created) creator.create(
				enriched(null, Optional.of("https://example.com/defaults2"), "AI", "body"));

		assertThat(created.item().sourceMessageId()).isZero();
		assertThat(created.item().userLibType()).isEmpty();
		assertThat(created.item().userLibItemId()).isEmpty();
	}

	private static SavedItem enriched(Long id, Optional<String> url, String theme, String body) {
		return new SavedItem(
				id,
				CHAT_ID,
				url,
				body,
				theme,
				0L,
				Optional.empty(),
				Optional.empty(),
				Optional.of(SourceType.ARTICLE),
				Optional.of("Title"),
				Optional.of("Anna"),
				List.of("tag"),
				Optional.of("english search text"));
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
