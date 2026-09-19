package com.fava.catalog;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seed-only loader for {@link CatalogSeedCorpus}: creates a test Catalog when missing,
 * persists enriched Saved Items with synthetic Theme Topic library pointers, and indexes them.
 */
public final class CatalogSeeder {

	private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

	/** Synthetic forum thread ids for seed Catalogs (not real Telegram topics). */
	static final long SEED_INBOX_THREAD = 101L;
	static final long SEED_SMART_SEARCH_THREAD = 102L;
	static final long SEED_THEME_THREAD_BASE = 201L;

	private final CatalogStore catalogs;
	private final SavedItemStore savedItems;
	private final SavedItemIndexer indexer;

	public CatalogSeeder(CatalogStore catalogs, SavedItemStore savedItems, SavedItemIndexer indexer) {
		this.catalogs = catalogs;
		this.savedItems = savedItems;
		this.indexer = indexer;
	}

	public Result seed(long chatId) {
		ensureCatalog(chatId);
		List<SavedItem> existing = savedItems.findByCatalogFilters(chatId, SavedItemFilters.NONE);
		if (!existing.isEmpty()) {
			log.info("Seed skipped for chat {}: Catalog already has {} Saved Items", chatId, existing.size());
			return new Result.AlreadySeeded(existing.size());
		}
		AtomicLong sourceMessageId = new AtomicLong(1);
		int count = 0;
		int instagramAgeDays = 90;
		Instant now = Instant.now();
		for (CatalogSeedCorpus.SeedDraft draft : CatalogSeedCorpus.items()) {
			SavedItem saved = savedItems.save(toSavedItem(chatId, draft, sourceMessageId.getAndIncrement()));
			SavedItem withLib = savedItems.updateUserLib(
					saved.id(), SavedItem.USER_LIB_TYPE_TELEGRAM, draft.userLibItemId());
			if (draft.sourceType() == SourceType.INSTAGRAM) {
				savedItems.updateCreatedAt(withLib.id(), now.minus(instagramAgeDays, ChronoUnit.DAYS));
				instagramAgeDays -= 12;
				if (instagramAgeDays < 7) {
					instagramAgeDays = 7;
				}
			}
			indexer.index(withLib);
			count++;
		}
		log.info("Seeded {} Saved Items into Catalog chat {}", count, chatId);
		return new Result.Seeded(count);
	}

	private void ensureCatalog(long chatId) {
		if (catalogs.isConfigured(chatId)) {
			return;
		}
		List<ThemeTopic> themes = new ArrayList<>();
		for (int i = 0; i < CatalogSeedCorpus.THEME_NAMES.size(); i++) {
			themes.add(new ThemeTopic(CatalogSeedCorpus.THEME_NAMES.get(i), SEED_THEME_THREAD_BASE + i));
		}
		CatalogCreateResult created = catalogs.create(new Catalog(
				chatId, SEED_INBOX_THREAD, SEED_SMART_SEARCH_THREAD, List.copyOf(themes)));
		if (!(created instanceof CatalogCreateResult.Created)
				&& !(created instanceof CatalogCreateResult.AlreadyConfigured)) {
			throw new IllegalStateException("Unexpected Catalog create result: " + created);
		}
	}

	private static SavedItem toSavedItem(long chatId, CatalogSeedCorpus.SeedDraft draft, long sourceMessageId) {
		return new SavedItem(
				null,
				chatId,
				draft.url(),
				draft.bodyText(),
				draft.themeName(),
				sourceMessageId,
				Optional.empty(),
				Optional.empty(),
				Optional.of(draft.sourceType()),
				Optional.of(draft.title()),
				draft.recommendedBy(),
				draft.tags(),
				Optional.of(draft.searchText()));
	}

	public sealed interface Result {
		record Seeded(int itemCount) implements Result {
		}

		record AlreadySeeded(int existingItemCount) implements Result {
		}
	}
}
