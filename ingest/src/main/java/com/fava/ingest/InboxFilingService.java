package com.fava.ingest;

import com.fava.catalog.Catalog;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SavedItemStore;
import com.fava.catalog.ThemeTopic;
import java.util.List;
import java.util.Optional;

/**
 * Files an Accepted Inbox draft: URL dedupe, stub Classifier Decision, persist, copy, confirm.
 *
 * <p>Stub Classifier Decision: picks the first Theme Topic in {@link Catalog#themes()} order
 * (stable {@code ORDER BY id} from persistence). Never Inbox or Smart Search.
 */
public final class InboxFilingService {

	static final String FILED_PREFIX = "Filed → ";
	static final String ALREADY_PREFIX = "Already saved → ";

	private final SavedItemStore savedItems;
	private final FilingPort filingPort;

	public InboxFilingService(SavedItemStore savedItems, FilingPort filingPort) {
		this.savedItems = savedItems;
		this.filingPort = filingPort;
	}

	public FilingResult file(AcceptedDraft draft, Catalog catalog) {
		if (draft.url().isPresent()) {
			Optional<SavedItem> existing = savedItems.findByCatalogAndUrl(catalog.chatId(), draft.url().get());
			if (existing.isPresent()) {
				String themeName = existing.get().themeName();
				filingPort.replyToMessage(draft.chatId(), draft.sourceMessageId(), ALREADY_PREFIX + themeName);
				return new FilingResult.AlreadyFiled(themeName);
			}
		}

		ThemeTopic theme = pickTheme(catalog);
		SavedItem saved = savedItems.save(new SavedItem(
				null,
				catalog.chatId(),
				draft.url(),
				draft.bodyText(),
				theme.name(),
				draft.sourceMessageId()));
		filingPort.copyMessageToThread(catalog.chatId(), draft.sourceMessageId(), theme.threadId());
		filingPort.replyToMessage(draft.chatId(), draft.sourceMessageId(), FILED_PREFIX + theme.name());
		return new FilingResult.Filed(theme.name(), saved);
	}

	/**
	 * Deterministic stub: first Theme Topic in catalog order.
	 */
	static ThemeTopic pickTheme(Catalog catalog) {
		List<ThemeTopic> themes = catalog.themes();
		if (themes.isEmpty()) {
			throw new IllegalStateException("Catalog has no Theme Topics to file into");
		}
		return themes.getFirst();
	}
}
