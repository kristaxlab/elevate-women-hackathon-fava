package com.fava.catalog;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * JDBC {@link SavedItemStore} backed by Postgres.
 */
public final class JdbcSavedItemStore implements SavedItemStore {

	private static final RowMapper<SavedItem> ROW = (rs, rowNum) -> new SavedItem(
			rs.getLong("id"),
			rs.getLong("chat_id"),
			Optional.ofNullable(rs.getString("url")),
			rs.getString("body_text"),
			rs.getString("theme_name"),
			rs.getLong("source_message_id"));

	private final JdbcTemplate jdbc;

	public JdbcSavedItemStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	@Override
	public SavedItem save(SavedItem item) {
		KeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
					"""
							INSERT INTO saved_items (chat_id, url, body_text, theme_name, source_message_id)
							VALUES (?, ?, ?, ?, ?)
							""",
					new String[] {"id"});
			ps.setLong(1, item.chatId());
			ps.setString(2, item.url().orElse(null));
			ps.setString(3, item.bodyText());
			ps.setString(4, item.themeName());
			ps.setLong(5, item.sourceMessageId());
			return ps;
		}, keys);
		Number key = keys.getKey();
		if (key == null) {
			throw new IllegalStateException("saved_items insert returned no id");
		}
		return new SavedItem(
				key.longValue(),
				item.chatId(),
				item.url(),
				item.bodyText(),
				item.themeName(),
				item.sourceMessageId());
	}

	@Override
	public Optional<SavedItem> findByCatalogAndUrl(long chatId, String url) {
		if (url == null || url.isBlank()) {
			return Optional.empty();
		}
		List<SavedItem> rows = jdbc.query(
				"""
						SELECT id, chat_id, url, body_text, theme_name, source_message_id
						FROM saved_items
						WHERE chat_id = ? AND url = ?
						ORDER BY id
						LIMIT 1
						""",
				ROW,
				chatId,
				url);
		return rows.stream().findFirst();
	}

	@Override
	public Optional<SavedItem> findById(long id) {
		List<SavedItem> rows = jdbc.query(
				"""
						SELECT id, chat_id, url, body_text, theme_name, source_message_id
						FROM saved_items
						WHERE id = ?
						""",
				ROW,
				id);
		return rows.stream().findFirst();
	}
}
