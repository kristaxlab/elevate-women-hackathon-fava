package com.fava.catalog;

import java.util.List;

/**
 * A personal collection of Saved Items bound to exactly one forum-enabled Telegram supergroup.
 */
public record Catalog(
		long chatId,
		long inboxThreadId,
		long smartSearchThreadId,
		List<ThemeTopic> themes) {

	public Catalog {
		themes = List.copyOf(themes);
	}
}
