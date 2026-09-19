package com.fava.catalog;

/**
 * pgvector column size for the OpenRouter / OpenAI embedding model used in production.
 * Must match {@code fava.openrouter.embedding-model} (default {@code openai/text-embedding-3-small} → 1536).
 */
public final class EmbeddingDimensions {

	/** Dimension count for {@code openai/text-embedding-3-small}. */
	public static final int OPENAI_TEXT_EMBEDDING_3_SMALL = 1536;

	private EmbeddingDimensions() {
	}
}
