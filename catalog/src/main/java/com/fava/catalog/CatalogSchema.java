package com.fava.catalog;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Applies Catalog persistence DDL (idempotent).
 */
public final class CatalogSchema {

	private CatalogSchema() {
	}

	/**
	 * Ensures core Catalog tables and an embeddings table sized to
	 * {@link EmbeddingSpace#DEFAULT}.
	 */
	public static void ensure(DataSource dataSource) {
		ensure(dataSource, EmbeddingSpace.DEFAULT);
	}

	/**
	 * Ensures core Catalog tables, the embedding-models registry, and
	 * {@code saved_item_embeddings} sized to {@code space.dimensions()}.
	 * If the embeddings table already exists with a different dimension (or without
	 * {@code embedding_model_id}), it is dropped and recreated.
	 */
	public static void ensure(DataSource dataSource, EmbeddingSpace space) {
		ensure(dataSource, space.dimensions());
	}

	/**
	 * @see #ensure(DataSource, EmbeddingSpace)
	 */
	public static void ensure(DataSource dataSource, int embeddingDimensions) {
		// Validate via EmbeddingSpace (positive dimensions).
		new EmbeddingSpace(EmbeddingSpace.DEFAULT.modelId(), embeddingDimensions);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("""
				CREATE TABLE IF NOT EXISTS catalogs (
					chat_id BIGINT PRIMARY KEY,
					inbox_thread_id BIGINT NOT NULL,
					smart_search_thread_id BIGINT NOT NULL,
					created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
				)
				""");
		jdbc.execute("""
				CREATE TABLE IF NOT EXISTS theme_topics (
					id BIGSERIAL PRIMARY KEY,
					chat_id BIGINT NOT NULL REFERENCES catalogs (chat_id) ON DELETE CASCADE,
					name TEXT NOT NULL,
					thread_id BIGINT NOT NULL,
					UNIQUE (chat_id, name),
					UNIQUE (chat_id, thread_id)
				)
				""");
		jdbc.execute("""
				CREATE TABLE IF NOT EXISTS saved_items (
					id BIGSERIAL PRIMARY KEY,
					chat_id BIGINT NOT NULL REFERENCES catalogs (chat_id) ON DELETE CASCADE,
					url TEXT,
					body_text TEXT NOT NULL,
					theme_name TEXT NOT NULL,
					source_message_id BIGINT NOT NULL,
					created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
				)
				""");
		jdbc.execute("""
				CREATE UNIQUE INDEX IF NOT EXISTS saved_items_chat_url_uidx
				ON saved_items (chat_id, url)
				WHERE url IS NOT NULL
				""");
		jdbc.execute("""
				CREATE TABLE IF NOT EXISTS embedding_models (
					id BIGSERIAL PRIMARY KEY,
					model_id TEXT NOT NULL,
					dimensions INT NOT NULL,
					is_active BOOLEAN NOT NULL DEFAULT FALSE,
					catalog_sync_status TEXT NOT NULL
				)
				""");
		jdbc.execute("""
				CREATE UNIQUE INDEX IF NOT EXISTS embedding_models_one_active
				ON embedding_models ((is_active))
				WHERE is_active
				""");
		jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
		ensureEmbeddingsTable(jdbc, embeddingDimensions);
	}

	/**
	 * Drops and recreates {@code saved_item_embeddings} with {@code embeddingDimensions}.
	 */
	public static void recreateEmbeddingsTable(DataSource dataSource, int embeddingDimensions) {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("DROP TABLE IF EXISTS saved_item_embeddings");
		createEmbeddingsTable(jdbc, embeddingDimensions);
	}

	private static void ensureEmbeddingsTable(JdbcTemplate jdbc, int embeddingDimensions) {
		Integer existingDims = jdbc.query(
				"""
						SELECT a.atttypmod
						FROM pg_attribute a
						JOIN pg_class c ON a.attrelid = c.oid
						JOIN pg_namespace n ON c.relnamespace = n.oid
						WHERE c.relname = 'saved_item_embeddings'
						  AND a.attname = 'embedding'
						  AND n.nspname = current_schema()
						  AND NOT a.attisdropped
						""",
				rs -> rs.next() ? rs.getInt(1) : null);
		boolean hasModelId = Boolean.TRUE.equals(jdbc.query(
				"""
						SELECT 1
						FROM information_schema.columns
						WHERE table_schema = current_schema()
						  AND table_name = 'saved_item_embeddings'
						  AND column_name = 'embedding_model_id'
						""",
				rs -> rs.next() ? Boolean.TRUE : Boolean.FALSE));
		if ((existingDims != null && existingDims != embeddingDimensions) || (existingDims != null && !hasModelId)) {
			jdbc.execute("DROP TABLE IF EXISTS saved_item_embeddings");
		}
		createEmbeddingsTable(jdbc, embeddingDimensions);
	}

	private static void createEmbeddingsTable(JdbcTemplate jdbc, int embeddingDimensions) {
		jdbc.execute("""
				CREATE TABLE IF NOT EXISTS saved_item_embeddings (
					saved_item_id BIGINT PRIMARY KEY REFERENCES saved_items (id) ON DELETE CASCADE,
					chat_id BIGINT NOT NULL REFERENCES catalogs (chat_id) ON DELETE CASCADE,
					embedding vector(%d) NOT NULL,
					embedding_model_id BIGINT NOT NULL REFERENCES embedding_models (id)
				)
				""".formatted(embeddingDimensions));
	}
}
