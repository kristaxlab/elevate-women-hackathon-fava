package com.fava.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC {@link SavedItemEmbeddingStore} using pgvector cosine distance ({@code <=>}).
 */
public final class JdbcSavedItemEmbeddingStore implements SavedItemEmbeddingStore {

	private final JdbcTemplate jdbc;
	private final int dimensions;

	public JdbcSavedItemEmbeddingStore(DataSource dataSource, EmbeddingSpace space) {
		this(dataSource, space.dimensions());
	}

	public JdbcSavedItemEmbeddingStore(DataSource dataSource, int dimensions) {
		this.dimensions = new EmbeddingSpace(EmbeddingSpace.DEFAULT.modelId(), dimensions).dimensions();
		this.jdbc = new JdbcTemplate(dataSource);
	}

	public JdbcSavedItemEmbeddingStore(DataSource dataSource) {
		this(dataSource, EmbeddingSpace.DEFAULT);
	}

	@Override
	public void upsert(long savedItemId, long chatId, float[] embedding, long embeddingModelId) {
		requireDims(embedding);
		String literal = toVectorLiteral(embedding);
		jdbc.update(
				"""
						INSERT INTO saved_item_embeddings (saved_item_id, chat_id, embedding, embedding_model_id)
						VALUES (?, ?, ?::vector, ?)
						ON CONFLICT (saved_item_id) DO UPDATE SET
							chat_id = EXCLUDED.chat_id,
							embedding = EXCLUDED.embedding,
							embedding_model_id = EXCLUDED.embedding_model_id
						""",
				savedItemId,
				chatId,
				literal,
				embeddingModelId);
	}

	@Override
	public Optional<StoredEmbedding> findBySavedItemId(long savedItemId) {
		List<StoredEmbedding> rows = jdbc.query(
				"""
						SELECT e.embedding::text AS embedding, m.model_id, m.dimensions
						FROM saved_item_embeddings e
						JOIN embedding_models m ON m.id = e.embedding_model_id
						WHERE e.saved_item_id = ?
						""",
				(rs, rowNum) -> {
					int dims = rs.getInt("dimensions");
					float[] vector = parseVectorLiteral(rs.getString("embedding"), dims);
					return new StoredEmbedding(rs.getString("model_id"), dims, vector);
				},
				savedItemId);
		return rows.stream().findFirst();
	}

	@Override
	public List<SavedItemHit> findSimilar(long chatId, float[] queryEmbedding, int limit, double maxDistance) {
		requireDims(queryEmbedding);
		String literal = toVectorLiteral(queryEmbedding);
		return jdbc.query(
				"""
						SELECT saved_item_id, (embedding <=> ?::vector) AS distance
						FROM saved_item_embeddings
						WHERE chat_id = ?
						  AND (embedding <=> ?::vector) <= ?
						ORDER BY embedding <=> ?::vector
						LIMIT ?
						""",
				(rs, rowNum) -> new SavedItemHit(rs.getLong("saved_item_id"), rs.getDouble("distance")),
				literal,
				chatId,
				literal,
				maxDistance,
				literal,
				limit);
	}

	@Override
	public List<SavedItemHit> findSimilarAmong(
			long chatId,
			float[] queryEmbedding,
			int limit,
			double maxDistance,
			Collection<Long> candidateIds) {
		if (candidateIds == null || candidateIds.isEmpty()) {
			return List.of();
		}
		requireDims(queryEmbedding);
		String literal = toVectorLiteral(queryEmbedding);
		Long[] ids = candidateIds.toArray(Long[]::new);
		return jdbc.query(
				connection -> {
					var ps = connection.prepareStatement(
							"""
									SELECT saved_item_id, (embedding <=> ?::vector) AS distance
									FROM saved_item_embeddings
									WHERE chat_id = ?
									  AND saved_item_id = ANY(?)
									  AND (embedding <=> ?::vector) <= ?
									ORDER BY embedding <=> ?::vector
									LIMIT ?
									""");
					ps.setString(1, literal);
					ps.setLong(2, chatId);
					ps.setArray(3, connection.createArrayOf("bigint", ids));
					ps.setString(4, literal);
					ps.setDouble(5, maxDistance);
					ps.setString(6, literal);
					ps.setInt(7, limit);
					return ps;
				},
				(rs, rowNum) -> new SavedItemHit(rs.getLong("saved_item_id"), rs.getDouble("distance")));
	}

	@Override
	public void deleteAll() {
		jdbc.update("DELETE FROM saved_item_embeddings");
	}

	@Override
	public List<SavedItem> findNeedingEmbedding(long activeEmbeddingModelId) {
		return jdbc.query(
				"""
						SELECT si.id, si.chat_id, si.url, si.body_text, si.theme_name, si.source_message_id,
							si.user_lib_type, si.user_lib_item_id, si.source_type, si.title,
							si.recommended_by, si.tags, si.search_text
						FROM saved_items si
						LEFT JOIN saved_item_embeddings e ON e.saved_item_id = si.id
						WHERE e.saved_item_id IS NULL
						   OR e.embedding_model_id IS DISTINCT FROM ?
						ORDER BY si.id
						""",
				JdbcSavedItemStore.ROW,
				activeEmbeddingModelId);
	}

	private void requireDims(float[] embedding) {
		if (embedding == null || embedding.length != dimensions) {
			throw new IllegalArgumentException("embedding must have " + dimensions + " dimensions");
		}
	}

	static String toVectorLiteral(float[] embedding) {
		StringBuilder sb = new StringBuilder(embedding.length * 8);
		sb.append('[');
		for (int i = 0; i < embedding.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(embedding[i]);
		}
		sb.append(']');
		return sb.toString();
	}

	static float[] parseVectorLiteral(String literal, int expectedDimensions) {
		if (literal == null || literal.isBlank()) {
			throw new IllegalArgumentException("embedding literal must not be blank");
		}
		String trimmed = literal.trim();
		if (trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
			throw new IllegalArgumentException("embedding literal must be [..]");
		}
		String body = trimmed.substring(1, trimmed.length() - 1).trim();
		if (body.isEmpty()) {
			throw new IllegalArgumentException("embedding literal must have " + expectedDimensions + " dimensions");
		}
		String[] parts = body.split(",");
		if (parts.length != expectedDimensions) {
			throw new IllegalArgumentException(
					"embedding literal length " + parts.length + " != " + expectedDimensions);
		}
		float[] vector = new float[expectedDimensions];
		for (int i = 0; i < parts.length; i++) {
			vector[i] = Float.parseFloat(parts[i].trim());
		}
		return vector;
	}
}
