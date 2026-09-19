package com.fava.catalog;

/**
 * Default embedding vector size when none is configured (openai/text-embedding-3-small).
 */
public final class EmbeddingDimensions {

	/** Default dimension count for {@code openai/text-embedding-3-small}. */
	public static final int DEFAULT = 1536;

	/** Alias for {@link #DEFAULT} (openai/text-embedding-3-small). */
	public static final int OPENAI_TEXT_EMBEDDING_3_SMALL = DEFAULT;

	private EmbeddingDimensions() {
	}
}
