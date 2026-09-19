package com.fava.classify;

/**
 * Thin chat completion seam. Fake in tests; OpenAI-compatible HTTP in production.
 */
public interface ChatModelPort {

	/**
	 * Returns the assistant message text for a single-turn completion.
	 */
	String complete(String systemPrompt, String userMessage);
}
