package com.fava.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Catalog Setup through {@link CatalogSetupService} with real Postgres and a faked Telegram forum port.
 */
@Testcontainers
class CatalogSetupPersistenceTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("fava")
			.withUsername("fava")
			.withPassword("fava");

	private CatalogSetupService setup;
	private CatalogStore store;
	private FakeForumPort forum;

	@BeforeEach
	void setUp() {
		DataSource dataSource = dataSource();
		CatalogSchema.ensure(dataSource);
		new JdbcTemplate(dataSource).execute("TRUNCATE theme_topics, catalogs CASCADE");
		store = new JdbcCatalogStore(dataSource);
		forum = new FakeForumPort();
		forum.forum = true;
		setup = new DefaultCatalogSetupService(store, forum);
	}

	@Test
	void setup_persistsSystemAndThemeThreadIds_andSecondSetupRefusesWithoutExtraTopics() {
		CatalogSetupResult first = setup.setup(-100777L, "AI, Fitness");

		assertThat(first).isInstanceOf(CatalogSetupResult.Success.class);
		Catalog catalog = ((CatalogSetupResult.Success) first).catalog();
		assertThat(store.findByChatId(-100777L)).contains(catalog);
		assertThat(forum.createdNames).containsExactly("Inbox", "Smart Search", "AI", "Fitness");

		int createdAfterFirst = forum.createdNames.size();
		CatalogSetupResult second = setup.setup(-100777L, "Other");

		assertThat(second).isInstanceOf(CatalogSetupResult.AlreadyConfigured.class);
		assertThat(forum.createdNames).hasSize(createdAfterFirst);
		assertThat(store.findByChatId(-100777L)).contains(catalog);
	}

	private static DataSource dataSource() {
		DriverManagerDataSource ds = new DriverManagerDataSource();
		ds.setUrl(POSTGRES.getJdbcUrl());
		ds.setUsername(POSTGRES.getUsername());
		ds.setPassword(POSTGRES.getPassword());
		ds.setDriverClassName("org.postgresql.Driver");
		return ds;
	}

	private static final class FakeForumPort implements CatalogForumPort {
		boolean forum;
		final List<String> createdNames = new ArrayList<>();
		private final AtomicLong nextId = new AtomicLong(500);

		@Override
		public boolean isForum(long chatId) {
			return forum;
		}

		@Override
		public long createForumTopic(long chatId, String name) {
			createdNames.add(name);
			return nextId.getAndIncrement();
		}
	}
}
