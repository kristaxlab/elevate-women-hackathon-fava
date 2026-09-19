package com.fava.catalog;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Applies Catalog persistence DDL (idempotent).
 */
public final class CatalogSchema {

	private CatalogSchema() {
	}

	public static void ensure(DataSource dataSource) {
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
	}
}
