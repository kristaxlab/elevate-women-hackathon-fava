package com.fava.classify;

/**
 * Thin embedding seam. Fake in tests; OpenAI-compatible HTTP in production.
 * Production vectors use 1536 dimensions ({@code openai/text-embedding-3-small}).
 */
public interface EmbeddingPort {

	/**
	 * Returns a dense embedding vector for {@code text}.
	 */
	float[] embed(String text);
}
