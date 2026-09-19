package com.fava.catalog;

/**
 * Default embedding vector size when none is configured (openai/text-embedding-3-small).
 */
public final class EmbeddingDimensions {

	/** Default dimension count for {@link EmbeddingSpace#DEFAULT}. */
	public static final int DEFAULT = 1536;

	private EmbeddingDimensions() {
	}
}
