package com.fava.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
class JdbcCatalogStoreTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private CatalogStore store;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource);
		new JdbcTemplate(dataSource).execute("TRUNCATE theme_topics, catalogs CASCADE");
		store = new JdbcCatalogStore(dataSource);
	}

	@Test
	void create_persistsCatalogWithSystemAndThemeTopics_retrievableByChatId() {
		Catalog catalog = new Catalog(
				-100123L,
				11L,
				22L,
				List.of(new ThemeTopic("AI", 31L), new ThemeTopic("Fitness", 32L)));

		CatalogCreateResult result = store.create(catalog);

		assertThat(result).isInstanceOf(CatalogCreateResult.Created.class);
		assertThat(store.isConfigured(-100123L)).isTrue();
		assertThat(store.findByChatId(-100123L)).contains(catalog);
	}

	@Test
	void create_secondSetupForSameChat_isRejectedAndLeavesOriginalIntact() {
		Catalog first = new Catalog(
				-100456L,
				1L,
				2L,
				List.of(new ThemeTopic("AI", 3L)));
		assertThat(store.create(first)).isInstanceOf(CatalogCreateResult.Created.class);

		CatalogCreateResult second = store.create(new Catalog(
				-100456L,
				99L,
				98L,
				List.of(new ThemeTopic("Other", 97L))));

		assertThat(second).isInstanceOf(CatalogCreateResult.AlreadyConfigured.class);
		assertThat(((CatalogCreateResult.AlreadyConfigured) second).existing()).isEqualTo(first);
		assertThat(store.findByChatId(-100456L)).contains(first);
	}

	@Test
	void findByChatId_whenMissing_isEmpty_andNotConfigured() {
		assertThat(store.findByChatId(-1L)).isEmpty();
		assertThat(store.isConfigured(-1L)).isFalse();
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
