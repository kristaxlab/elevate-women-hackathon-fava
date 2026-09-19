package com.fava.catalog;

import java.util.List;
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
}
