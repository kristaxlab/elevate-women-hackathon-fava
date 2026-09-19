package com.fava.catalog;

import static org.assertj.core.api.Assertions.assertThat;

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
class CatalogSeederTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private static final long CHAT_ID = -100160016L;

	private CatalogStore catalogs;
	private SavedItemStore savedItems;
	private RecordingIndexer indexer;
	private CatalogSeeder seeder;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource);
		new JdbcTemplate(dataSource)
				.execute("TRUNCATE saved_item_embeddings, saved_items, theme_topics, catalogs, embedding_models CASCADE");
		catalogs = new JdbcCatalogStore(dataSource);
		savedItems = new JdbcSavedItemStore(dataSource);
		indexer = new RecordingIndexer();
		seeder = new CatalogSeeder(catalogs, savedItems, indexer);
	}

	@Test
	void seed_createsCatalogAndPersistsEnrichedItemsWithLibraryPointersAndIndexes() {
		CatalogSeeder.Result result = seeder.seed(CHAT_ID);

		assertThat(result).isInstanceOf(CatalogSeeder.Result.Seeded.class);
		CatalogSeeder.Result.Seeded seeded = (CatalogSeeder.Result.Seeded) result;
		assertThat(seeded.itemCount()).isEqualTo(CatalogSeedCorpus.items().size());

		Catalog catalog = catalogs.findByChatId(CHAT_ID).orElseThrow();
		assertThat(catalog.themes()).extracting(ThemeTopic::name)
				.containsExactlyElementsOf(CatalogSeedCorpus.THEME_NAMES);

		List<SavedItem> loaded = savedItems.findByCatalogFilters(CHAT_ID, SavedItemFilters.NONE);
		assertThat(loaded).hasSize(CatalogSeedCorpus.items().size());
		assertThat(loaded).allSatisfy(item -> {
			assertThat(item.userLibType()).contains(SavedItem.USER_LIB_TYPE_TELEGRAM);
			assertThat(item.userLibItemId()).isPresent();
			assertThat(item.sourceType()).isPresent();
			assertThat(item.title()).isPresent();
			assertThat(item.searchText()).isPresent();
			assertThat(item.tags()).isNotEmpty();
		});
		assertThat(indexer.indexed.get()).isEqualTo(loaded.size());

		long olderInstagram = new JdbcTemplate(dataSource())
				.queryForObject(
						"""
								SELECT COUNT(*) FROM saved_items
								WHERE chat_id = ? AND source_type = 'instagram'
								  AND created_at < NOW() - INTERVAL '14 days'
								""",
						Long.class,
						CHAT_ID);
		assertThat(olderInstagram).isGreaterThanOrEqualTo(2);
	}

	@Test
	void seed_whenCatalogAlreadyHasItems_isIdempotentSkip() {
		seeder.seed(CHAT_ID);

		CatalogSeeder.Result second = seeder.seed(CHAT_ID);

		assertThat(second).isInstanceOf(CatalogSeeder.Result.AlreadySeeded.class);
		assertThat(savedItems.findByCatalogFilters(CHAT_ID, SavedItemFilters.NONE))
				.hasSize(CatalogSeedCorpus.items().size());
		assertThat(indexer.indexed.get()).isEqualTo(CatalogSeedCorpus.items().size());
	}

	private static DataSource dataSource() {
		DriverManagerDataSource ds = new DriverManagerDataSource();
		ds.setUrl(POSTGRES.getJdbcUrl());
		ds.setUsername(POSTGRES.getUsername());
		ds.setPassword(POSTGRES.getPassword());
		ds.setDriverClassName("org.postgresql.Driver");
		return ds;
	}

	private static final class RecordingIndexer implements SavedItemIndexer {
		final AtomicInteger indexed = new AtomicInteger();
		final List<Long> ids = new ArrayList<>();

		@Override
		public void index(SavedItem item) {
			indexed.incrementAndGet();
			ids.add(item.id());
		}
	}
}
