package com.fava.catalog;

/**
 * Port for Telegram forum operations needed during Catalog Setup.
 * Fake this in unit tests; the telegram module adapts the Bot API.
 */
public interface CatalogForumPort {

	boolean isForum(long chatId);

	/**
	 * Creates a forum topic and returns its {@code message_thread_id}.
	 */
	long createForumTopic(long chatId, String name);
}
