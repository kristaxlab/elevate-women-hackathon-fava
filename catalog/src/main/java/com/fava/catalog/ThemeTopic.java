package com.fava.catalog;

/**
 * A user-defined filing category within a Catalog, bound to a Telegram forum topic thread.
 */
public record ThemeTopic(String name, long threadId) {

	public ThemeTopic {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Theme Topic name must not be blank");
		}
	}
}
