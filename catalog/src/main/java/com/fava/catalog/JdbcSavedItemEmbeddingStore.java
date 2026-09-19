package com.fava.catalog;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC {@link SavedItemEmbeddingStore} using pgvector cosine distance ({@code <=>}).
 */
public final class JdbcSavedItemEmbeddingStore implements SavedItemEmbeddingStore {

	private final JdbcTemplate jdbc;

	public JdbcSavedItemEmbeddingStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	@Override
	public void upsert(long savedItemId, long chatId, float[] embedding) {
		requireDims(embedding);
		String literal = toVectorLiteral(embedding);
		jdbc.update(
				"""
						INSERT INTO saved_item_embeddings (saved_item_id, chat_id, embedding)
						VALUES (?, ?, ?::vector)
						ON CONFLICT (saved_item_id) DO UPDATE SET
							chat_id = EXCLUDED.chat_id,
							embedding = EXCLUDED.embedding
						""",
				savedItemId,
				chatId,
				literal);
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

	private static void requireDims(float[] embedding) {
		if (embedding == null || embedding.length != EmbeddingDimensions.OPENAI_TEXT_EMBEDDING_3_SMALL) {
			throw new IllegalArgumentException(
					"embedding must have " + EmbeddingDimensions.OPENAI_TEXT_EMBEDDING_3_SMALL + " dimensions");
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
}
