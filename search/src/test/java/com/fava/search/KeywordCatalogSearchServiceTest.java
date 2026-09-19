package com.fava.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fava.catalog.Catalog;
import com.fava.catalog.CatalogSchema;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.JdbcCatalogStore;
import com.fava.catalog.JdbcSavedItemStore;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemStore;
import com.fava.catalog.ThemeTopic;
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
class KeywordCatalogSearchServiceTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private static final long CHAT_ID = -100222L;

	private SavedItemStore savedItems;
	private KeywordCatalogSearchService search;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource);
		new JdbcTemplate(dataSource)
				.execute("TRUNCATE saved_item_embeddings, saved_items, theme_topics, catalogs, embedding_models CASCADE");
		CatalogStore catalogs = new JdbcCatalogStore(dataSource);
		savedItems = new JdbcSavedItemStore(dataSource);
		catalogs.create(new Catalog(CHAT_ID, 1L, 2L, List.of(new ThemeTopic("AI", 3L))));
		search = new KeywordCatalogSearchService(savedItems, 5);
	}

	@Test
	void emptyCatalog_returnsNothingFound() {
		assertThat(search.answer(CHAT_ID, "pilates tips"))
				.isInstanceOf(CatalogSearchResult.NothingFound.class);
	}

	@Test
	void keywordHit_returnsAnswerWithCitationUrl_withoutInventing() {
		savedItems.save(new SavedItem(
				null,
				CHAT_ID,
				Optional.of("https://example.com/pilates"),
				"Pilates reformer tip for beginners",
				"AI",
				1L));

		CatalogSearchResult result = search.answer(CHAT_ID, "any pilates advice?");

		assertThat(result).isInstanceOf(CatalogSearchResult.Answer.class);
		CatalogSearchResult.Answer answer = (CatalogSearchResult.Answer) result;
		assertThat(answer.text()).isEqualTo(KeywordCatalogSearchService.DEGRADED_INTRO);
		assertThat(answer.citations()).hasSize(1);
		assertThat(answer.citations().getFirst().snippet()).contains("Pilates reformer");
		assertThat(answer.citations().getFirst().url()).contains("https://example.com/pilates");
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
