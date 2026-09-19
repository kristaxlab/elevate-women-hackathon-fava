package com.fava.catalog;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC {@link CatalogStore} backed by Postgres.
 */
public final class JdbcCatalogStore implements CatalogStore {

	private static final RowMapper<CatalogRow> CATALOG_ROW = (rs, rowNum) -> new CatalogRow(
			rs.getLong("chat_id"),
			rs.getLong("inbox_thread_id"),
			rs.getLong("smart_search_thread_id"));

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;

	public JdbcCatalogStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
	}

	@Override
	public CatalogCreateResult create(Catalog catalog) {
		Optional<Catalog> existing = findByChatId(catalog.chatId());
		if (existing.isPresent()) {
			return new CatalogCreateResult.AlreadyConfigured(existing.get());
		}
		try {
			return transactions.execute(status -> {
				jdbc.update(
						"""
								INSERT INTO catalogs (chat_id, inbox_thread_id, smart_search_thread_id)
								VALUES (?, ?, ?)
								""",
						catalog.chatId(),
						catalog.inboxThreadId(),
						catalog.smartSearchThreadId());
				for (ThemeTopic theme : catalog.themes()) {
					jdbc.update(
							"""
									INSERT INTO theme_topics (chat_id, name, thread_id)
									VALUES (?, ?, ?)
									""",
							catalog.chatId(),
							theme.name(),
							theme.threadId());
				}
				return new CatalogCreateResult.Created(catalog);
			});
		}
		catch (DuplicateKeyException e) {
			return findByChatId(catalog.chatId())
					.<CatalogCreateResult>map(CatalogCreateResult.AlreadyConfigured::new)
					.orElseThrow(() -> e);
		}
	}

	@Override
	public Optional<Catalog> findByChatId(long chatId) {
		List<CatalogRow> rows = jdbc.query(
				"""
						SELECT chat_id, inbox_thread_id, smart_search_thread_id
						FROM catalogs
						WHERE chat_id = ?
						""",
				CATALOG_ROW,
				chatId);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		CatalogRow row = rows.getFirst();
		List<ThemeTopic> themes = jdbc.query(
				"""
						SELECT name, thread_id
						FROM theme_topics
						WHERE chat_id = ?
						ORDER BY id
						""",
				(ResultSet rs, int rowNum) -> new ThemeTopic(rs.getString("name"), rs.getLong("thread_id")),
				chatId);
		return Optional.of(new Catalog(row.chatId(), row.inboxThreadId(), row.smartSearchThreadId(), themes));
	}

	@Override
	public boolean isConfigured(long chatId) {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM catalogs WHERE chat_id = ?",
				Integer.class,
				chatId);
		return count != null && count > 0;
	}

	private record CatalogRow(long chatId, long inboxThreadId, long smartSearchThreadId) {
	}
}
