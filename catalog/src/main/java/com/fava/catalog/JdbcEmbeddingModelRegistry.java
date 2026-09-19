package com.fava.catalog;

import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * JDBC {@link EmbeddingModelRegistry}.
 */
public final class JdbcEmbeddingModelRegistry implements EmbeddingModelRegistry {

	private static final RowMapper<EmbeddingModel> ROW = (rs, rowNum) -> new EmbeddingModel(
			rs.getLong("id"),
			rs.getString("model_id"),
			rs.getInt("dimensions"),
			rs.getBoolean("is_active"),
			CatalogSyncStatus.valueOf(rs.getString("catalog_sync_status")));

	private final JdbcTemplate jdbc;

	public JdbcEmbeddingModelRegistry(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	@Override
	public Optional<EmbeddingModel> findActive() {
		List<EmbeddingModel> rows = jdbc.query(
				"""
						SELECT id, model_id, dimensions, is_active, catalog_sync_status
						FROM embedding_models
						WHERE is_active = TRUE
						LIMIT 1
						""",
				ROW);
		return rows.stream().findFirst();
	}

	@Override
	public EmbeddingModel activate(EmbeddingSpace space, CatalogSyncStatus status) {
		jdbc.update("UPDATE embedding_models SET is_active = FALSE WHERE is_active = TRUE");
		KeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			var ps = connection.prepareStatement(
					"""
							INSERT INTO embedding_models (model_id, dimensions, is_active, catalog_sync_status)
							VALUES (?, ?, TRUE, ?)
							""",
					new String[] {"id"});
			ps.setString(1, space.modelId());
			ps.setInt(2, space.dimensions());
			ps.setString(3, status.name());
			return ps;
		}, keys);
		Number key = keys.getKey();
		if (key == null) {
			throw new IllegalStateException("embedding_models insert returned no id");
		}
		return new EmbeddingModel(key.longValue(), space.modelId(), space.dimensions(), true, status);
	}

	@Override
	public void updateSyncStatus(long id, CatalogSyncStatus status) {
		int updated = jdbc.update(
				"UPDATE embedding_models SET catalog_sync_status = ? WHERE id = ?",
				status.name(),
				id);
		if (updated != 1) {
			throw new IllegalArgumentException("embedding model not found: " + id);
		}
	}
}
