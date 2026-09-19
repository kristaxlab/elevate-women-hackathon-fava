package com.fava.catalog;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

	static final RowMapper<SavedItem> ROW = (rs, rowNum) -> mapRow(rs);

	private final JdbcTemplate jdbc;

	public JdbcSavedItemStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	static SavedItem mapRow(ResultSet rs) throws SQLException {
		String sourceTypeWire = rs.getString("source_type");
		return new SavedItem(
				rs.getLong("id"),
				rs.getLong("chat_id"),
				Optional.ofNullable(rs.getString("url")),
				rs.getString("body_text"),
				rs.getString("theme_name"),
				rs.getLong("source_message_id"),
				Optional.ofNullable(rs.getString("user_lib_type")),
				Optional.ofNullable(rs.getString("user_lib_item_id")),
				sourceTypeWire == null || sourceTypeWire.isBlank()
						? Optional.empty()
						: Optional.of(SourceType.fromWire(sourceTypeWire)),
				Optional.ofNullable(rs.getString("title")),
				Optional.ofNullable(rs.getString("recommended_by")),
				readTags(rs.getArray("tags")),
				Optional.ofNullable(rs.getString("search_text")));
	}

	private static List<String> readTags(Array array) throws SQLException {
		if (array == null) {
			return List.of();
		}
		Object raw = array.getArray();
		if (raw instanceof String[] strings) {
			return List.of(strings);
		}
		if (raw instanceof Object[] objects) {
			return Arrays.stream(objects).map(String::valueOf).toList();
		}
		return List.of();
	}

	private static final String SELECT_COLUMNS = """
			id, chat_id, url, body_text, theme_name, source_message_id,
			user_lib_type, user_lib_item_id, source_type, title, recommended_by, tags, search_text
			""";

	@Override
	public SavedItem save(SavedItem item) {
		KeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
					"""
							INSERT INTO saved_items (
								chat_id, url, body_text, theme_name, source_message_id,
								user_lib_type, user_lib_item_id, source_type, title, recommended_by, tags, search_text
							)
							VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
							""",
					new String[] {"id"});
			ps.setLong(1, item.chatId());
			ps.setString(2, item.url().orElse(null));
			ps.setString(3, item.bodyText());
			ps.setString(4, item.themeName());
			ps.setLong(5, item.sourceMessageId());
			ps.setString(6, item.userLibType().orElse(null));
			ps.setString(7, item.userLibItemId().orElse(null));
			ps.setString(8, item.sourceType().map(SourceType::wireValue).orElse(null));
			ps.setString(9, item.title().orElse(null));
			ps.setString(10, item.recommendedBy().orElse(null));
			Array tags = connection.createArrayOf("text", item.tags().toArray());
			ps.setArray(11, tags);
			ps.setString(12, item.searchText().orElse(null));
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
				item.sourceMessageId(),
				item.userLibType(),
				item.userLibItemId(),
				item.sourceType(),
				item.title(),
				item.recommendedBy(),
				item.tags(),
				item.searchText());
	}

	@Override
	public SavedItem updateUserLib(long id, String userLibType, String userLibItemId) {
		if (userLibType == null || userLibType.isBlank()) {
			throw new IllegalArgumentException("userLibType must not be blank");
		}
		if (userLibItemId == null || userLibItemId.isBlank()) {
			throw new IllegalArgumentException("userLibItemId must not be blank");
		}
		int updated = jdbc.update(
				"""
						UPDATE saved_items
						SET user_lib_type = ?, user_lib_item_id = ?
						WHERE id = ?
						""",
				userLibType,
				userLibItemId,
				id);
		if (updated != 1) {
			throw new IllegalStateException("saved_items updateUserLib affected " + updated + " rows for id " + id);
		}
		return findById(id).orElseThrow(() -> new IllegalStateException("saved_items missing after updateUserLib: " + id));
	}

	@Override
	public void updateCreatedAt(long id, Instant createdAt) {
		if (createdAt == null) {
			throw new IllegalArgumentException("createdAt must not be null");
		}
		int updated = jdbc.update(
				"""
						UPDATE saved_items
						SET created_at = ?
						WHERE id = ?
						""",
				Timestamp.from(createdAt),
				id);
		if (updated != 1) {
			throw new IllegalStateException("saved_items updateCreatedAt affected " + updated + " rows for id " + id);
		}
	}

	@Override
	public Optional<SavedItem> findByCatalogAndUrl(long chatId, String url) {
		if (url == null || url.isBlank()) {
			return Optional.empty();
		}
		List<SavedItem> rows = jdbc.query(
				"""
						SELECT %s
						FROM saved_items
						WHERE chat_id = ? AND url = ?
						ORDER BY id
						LIMIT 1
						""".formatted(SELECT_COLUMNS),
				ROW,
				chatId,
				url);
		return rows.stream().findFirst();
	}

	@Override
	public Optional<SavedItem> findById(long id) {
		List<SavedItem> rows = jdbc.query(
				"""
						SELECT %s
						FROM saved_items
						WHERE id = ?
						""".formatted(SELECT_COLUMNS),
				ROW,
				id);
		return rows.stream().findFirst();
	}

	@Override
	public List<SavedItem> findByCatalogFilters(long chatId, SavedItemFilters filters) {
		SavedItemFilters f = filters == null ? SavedItemFilters.NONE : filters;
		StringBuilder sql = new StringBuilder(
				"""
						SELECT %s
						FROM saved_items
						WHERE chat_id = ?
						""".formatted(SELECT_COLUMNS));
		List<Object> args = new ArrayList<>();
		args.add(chatId);
		f.sourceType().ifPresent(type -> {
			sql.append(" AND source_type = ?");
			args.add(type.trim().toLowerCase(Locale.ROOT));
		});
		f.since().ifPresent(since -> {
			sql.append(" AND created_at >= ?");
			args.add(Timestamp.from(since));
		});
		f.recommendedBy().ifPresent(who -> {
			sql.append(" AND LOWER(recommended_by) = LOWER(?)");
			args.add(who.trim());
		});
		f.themeName().ifPresent(theme -> {
			sql.append(" AND LOWER(theme_name) = LOWER(?)");
			args.add(theme.trim());
		});
		final boolean hasTags = !f.tags().isEmpty();
		if (hasTags) {
			sql.append(" AND tags @> ?");
		}
		sql.append(" ORDER BY id");
		String query = sql.toString();
		List<String> tagValues = f.tags();
		return jdbc.query(
				connection -> {
					PreparedStatement ps = connection.prepareStatement(query);
					int idx = 1;
					for (Object arg : args) {
						ps.setObject(idx++, arg);
					}
					if (hasTags) {
						ps.setArray(idx, connection.createArrayOf("text", tagValues.toArray()));
					}
					return ps;
				},
				ROW);
	}
}
