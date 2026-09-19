package com.fava.catalog;

/**
 * Orchestrates one-time Catalog Setup: parse themes, ensure forum mode, create
 * System Topics and Theme Topics via {@link CatalogForumPort}, persist via {@link CatalogStore}.
 */
public interface CatalogSetupService {

	/**
	 * @param chatId Telegram supergroup id
	 * @param themeListText comma- and/or newline-separated Theme Topic names
	 */
	CatalogSetupResult setup(long chatId, String themeListText);
}
