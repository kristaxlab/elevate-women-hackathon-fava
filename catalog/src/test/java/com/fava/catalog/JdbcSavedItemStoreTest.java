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
class JdbcSavedItemStoreTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private CatalogStore catalogStore;
	private SavedItemStore savedItemStore;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource);
		new JdbcTemplate(dataSource).execute("TRUNCATE saved_items, theme_topics, catalogs CASCADE");
		catalogStore = new JdbcCatalogStore(dataSource);
		savedItemStore = new JdbcSavedItemStore(dataSource);
		catalogStore.create(new Catalog(
				-100123L,
				11L,
				22L,
				List.of(new ThemeTopic("AI", 31L), new ThemeTopic("Fitness", 32L))));
	}

	@Test
	void save_persistsSavedItem_retrievableByCatalogAndUrl() {
		SavedItem saved = savedItemStore.save(SavedItem.of(
				null,
				-100123L,
				Optional.of("https://www.instagram.com/p/ABC/"),
				"caption text",
				"AI",
				7L));

		assertThat(saved.id()).isNotNull();
		assertThat(savedItemStore.findByCatalogAndUrl(-100123L, "https://www.instagram.com/p/ABC/"))
				.contains(saved);
	}

	@Test
	void findByCatalogAndUrl_whenMissing_isEmpty() {
		assertThat(savedItemStore.findByCatalogAndUrl(-100123L, "https://missing.example/"))
				.isEmpty();
	}

	@Test
	void findByCatalogAndUrl_doesNotMatchOtherCatalog() {
		savedItemStore.save(SavedItem.of(
				null,
				-100123L,
				Optional.of("https://shared.example/x"),
				"body",
				"Fitness",
				1L));

		assertThat(savedItemStore.findByCatalogAndUrl(-999L, "https://shared.example/x")).isEmpty();
	}

	@Test
	void save_withoutUrl_isPersistedAndNotFoundByUrlLookup() {
		SavedItem saved = savedItemStore.save(SavedItem.of(
				null,
				-100123L,
				Optional.empty(),
				"forwarded tip",
				"AI",
				9L));

		assertThat(saved.id()).isNotNull();
		assertThat(savedItemStore.findById(saved.id())).contains(saved);
	}

	@Test
	void save_withEnrichmentAndLibraryPointer_roundTripsAllNewFields() {
		SavedItem saved = savedItemStore.save(new SavedItem(
				null,
				-100123L,
				Optional.of("https://www.youtube.com/watch?v=abc"),
				"raw caption",
				"AI",
				42L,
				Optional.of("telegram"),
				Optional.of("9001"),
				Optional.of(SourceType.YOUTUBE),
				Optional.of("Great talk on embeddings"),
				Optional.of("Anna"),
				List.of("ml", "talk"),
				Optional.of("A YouTube talk about embeddings recommended by Anna")));

		assertThat(saved.id()).isNotNull();
		assertThat(savedItemStore.findById(saved.id())).contains(saved);
		assertThat(savedItemStore.findByCatalogAndUrl(-100123L, "https://www.youtube.com/watch?v=abc"))
				.contains(saved);
	}

	@Test
	void save_withoutEnrichmentFields_roundTripsAsEmptyDefaults() {
		SavedItem saved = savedItemStore.save(SavedItem.of(
				null,
				-100123L,
				Optional.of("https://example.com/bare"),
				"just a link",
				"Fitness",
				3L));

		assertThat(saved.userLibType()).isEmpty();
		assertThat(saved.userLibItemId()).isEmpty();
		assertThat(saved.sourceType()).isEmpty();
		assertThat(saved.title()).isEmpty();
		assertThat(saved.recommendedBy()).isEmpty();
		assertThat(saved.tags()).isEmpty();
		assertThat(saved.searchText()).isEmpty();
		assertThat(savedItemStore.findById(saved.id())).contains(saved);
	}

	@Test
	void findByCatalogFilters_tagsAreAnd_andHardEmptyWhenNothingMatches() {
		savedItemStore.save(new SavedItem(
				null,
				-100123L,
				Optional.empty(),
				"pasta",
				"AI",
				1L,
				Optional.empty(),
				Optional.empty(),
				Optional.of(SourceType.RECIPE),
				Optional.of("Pasta"),
				Optional.of("Anna"),
				List.of("italian", "dinner"),
				Optional.empty()));
		savedItemStore.save(new SavedItem(
				null,
				-100123L,
				Optional.empty(),
				"taco",
				"AI",
				2L,
				Optional.empty(),
				Optional.empty(),
				Optional.of(SourceType.RECIPE),
				Optional.of("Taco"),
				Optional.of("Bob"),
				List.of("mexican"),
				Optional.empty()));

		List<SavedItem> andHits = savedItemStore.findByCatalogFilters(
				-100123L,
				new SavedItemFilters(
						Optional.of("recipe"),
						Optional.empty(),
						Optional.of("Anna"),
						List.of("italian", "dinner"),
						Optional.empty()));
		assertThat(andHits).extracting(SavedItem::title).containsExactly(Optional.of("Pasta"));

		List<SavedItem> none = savedItemStore.findByCatalogFilters(
				-100123L,
				new SavedItemFilters(
						Optional.empty(),
						Optional.empty(),
						Optional.empty(),
						List.of("italian", "mexican"),
						Optional.empty()));
		assertThat(none).isEmpty();
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
