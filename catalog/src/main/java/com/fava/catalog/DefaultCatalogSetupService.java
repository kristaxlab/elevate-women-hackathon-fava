package com.fava.catalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Default Catalog Setup: parse themes, require forum mode, create System Topics + Theme Topics, persist.
 */
public final class DefaultCatalogSetupService implements CatalogSetupService {

	static final String INBOX_NAME = "Inbox";
	static final String SMART_SEARCH_NAME = "Smart Search";

	private final CatalogStore store;
	private final CatalogForumPort forum;

	public DefaultCatalogSetupService(CatalogStore store, CatalogForumPort forum) {
		this.store = store;
		this.forum = forum;
	}

	@Override
	public CatalogSetupResult setup(long chatId, String themeListText) {
		List<String> themes = parseThemeNames(themeListText);
		if (themes.isEmpty()) {
			return new CatalogSetupResult.EmptyThemeList();
		}
		if (store.isConfigured(chatId)) {
			Catalog existing = store.findByChatId(chatId)
					.orElseThrow(() -> new IllegalStateException("Catalog marked configured but missing for chat " + chatId));
			return new CatalogSetupResult.AlreadyConfigured(existing);
		}
		if (!forum.isForum(chatId)) {
			return new CatalogSetupResult.NeedsForum();
		}

		long inboxThreadId = forum.createForumTopic(chatId, INBOX_NAME);
		long smartSearchThreadId = forum.createForumTopic(chatId, SMART_SEARCH_NAME);
		List<ThemeTopic> themeTopics = new ArrayList<>();
		for (String name : themes) {
			long threadId = forum.createForumTopic(chatId, name);
			themeTopics.add(new ThemeTopic(name, threadId));
		}

		Catalog catalog = new Catalog(chatId, inboxThreadId, smartSearchThreadId, themeTopics);
		CatalogCreateResult persisted = store.create(catalog);
		return switch (persisted) {
			case CatalogCreateResult.Created created -> new CatalogSetupResult.Success(created.catalog());
			case CatalogCreateResult.AlreadyConfigured already ->
					new CatalogSetupResult.AlreadyConfigured(already.existing());
		};
	}

	/**
	 * Splits on commas and newlines, trims, drops blanks. Preserves first-seen order; keeps duplicates
	 * as distinct names only once (first occurrence wins) so Telegram topic names stay unique.
	 */
	static List<String> parseThemeNames(String themeListText) {
		if (themeListText == null || themeListText.isBlank()) {
			return List.of();
		}
		List<String> names = new ArrayList<>();
		for (String part : themeListText.split("[,\\n]+")) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty() && !names.contains(trimmed)) {
				names.add(trimmed);
			}
		}
		return List.copyOf(names);
	}
}
