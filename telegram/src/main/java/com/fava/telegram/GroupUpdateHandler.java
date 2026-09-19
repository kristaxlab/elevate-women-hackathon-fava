package com.fava.telegram;

import com.fava.catalog.Catalog;
import com.fava.catalog.CatalogSetupResult;
import com.fava.catalog.CatalogSetupService;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.ThemeTopic;
import com.fava.ingest.AcceptedDraft;
import com.fava.ingest.InboxFilingService;
import com.fava.ingest.InboxMessageNormalizer;
import com.fava.ingest.NormalizeResult;
import com.fava.ingest.ThemePickCallback;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;

/**
 * Routes group/supergroup updates for Catalog Setup and Inbox ingest:
 * <ul>
 *   <li>{@code my_chat_member} when Fava becomes admin → nudge toward Topics + {@code /setup}</li>
 *   <li>{@code /setup} with themes on the same message (e.g. {@code /setup AI, Fitness}), or
 *       {@code /setup} alone then the next message from the same admin with the theme list</li>
 *   <li>Messages in a configured Catalog's Inbox thread → normalize, file (or reject)</li>
 *   <li>Theme Topic pick {@code callback_query} → complete Filing</li>
 *   <li>Non-command messages in an unconfigured group → prompt admins to run {@code /setup}</li>
 * </ul>
 * Private chats are ignored (see {@link DmUpdateHandler}).
 */
public final class GroupUpdateHandler {

	static final String MSG_ADMIN_NUDGE = "fava.setup.admin_nudge";
	static final String MSG_NEEDS_FORUM = "fava.setup.needs_forum";
	static final String MSG_NOT_ADMIN = "fava.setup.not_admin";
	static final String MSG_ASK_THEMES = "fava.setup.ask_themes";
	static final String MSG_EMPTY_THEMES = "fava.setup.empty_themes";
	static final String MSG_ALREADY = "fava.setup.already_configured";
	static final String MSG_SUCCESS = "fava.setup.success";
	static final String MSG_PRE_SETUP = "fava.setup.pre_setup_prompt";

	private final MessageSource messages;
	private final TelegramOutbound outbound;
	private final CatalogStore catalogStore;
	private final CatalogSetupService setupService;
	private final ChatAdminPort chatAdminPort;
	private final Supplier<Long> botUserId;
	private final InboxMessageNormalizer inboxNormalizer;
	private final InboxFilingService inboxFiling;

	/** chatId → userId awaiting theme list after bare {@code /setup}. */
	private final Map<Long, Long> pendingThemeListByChat = new ConcurrentHashMap<>();

	public GroupUpdateHandler(
			MessageSource messages,
			TelegramOutbound outbound,
			CatalogStore catalogStore,
			CatalogSetupService setupService,
			ChatAdminPort chatAdminPort,
			Supplier<Long> botUserId,
			InboxMessageNormalizer inboxNormalizer,
			InboxFilingService inboxFiling) {
		this.messages = messages;
		this.outbound = outbound;
		this.catalogStore = catalogStore;
		this.setupService = setupService;
		this.chatAdminPort = chatAdminPort;
		this.botUserId = botUserId;
		this.inboxNormalizer = inboxNormalizer;
		this.inboxFiling = inboxFiling;
	}

	public void handle(TelegramUpdate update) {
		if (update == null) {
			return;
		}
		if (update.callbackQuery() != null) {
			handleCallbackQuery(update.callbackQuery());
			return;
		}
		if (update.myChatMember() != null) {
			handleMyChatMember(update.myChatMember());
			return;
		}
		if (update.message() == null) {
			return;
		}
		TelegramMessage message = update.message();
		TelegramChat chat = message.chat();
		if (chat == null || !isGroupChat(chat.type())) {
			return;
		}
		handleGroupMessage(message);
	}

	private void handleCallbackQuery(TelegramCallbackQuery query) {
		if (query.message() == null || query.message().chat() == null) {
			return;
		}
		TelegramChat chat = query.message().chat();
		if (!isGroupChat(chat.type())) {
			return;
		}
		Optional<ThemePickCallback.Parsed> pick = ThemePickCallback.parse(query.data());
		if (pick.isEmpty()) {
			return;
		}
		Optional<Catalog> catalog = catalogStore.findByChatId(chat.id());
		if (catalog.isEmpty()) {
			outbound.answerCallbackQuery(query.id());
			return;
		}
		inboxFiling.completeThemePick(
				query.id(),
				chat.id(),
				pick.get().sourceMessageId(),
				pick.get().themeIndex(),
				catalog.get());
	}

	private void handleMyChatMember(TelegramChatMemberUpdated change) {
		if (change.chat() == null || !isGroupChat(change.chat().type())) {
			return;
		}
		Long botId = botUserId.get();
		if (botId == null || change.newChatMember() == null || change.newChatMember().user() == null) {
			return;
		}
		if (change.newChatMember().user().id() != botId) {
			return;
		}
		if (!isAdminStatus(change.newChatMember().status())) {
			return;
		}
		String oldStatus = change.oldChatMember() == null ? null : change.oldChatMember().status();
		if (isAdminStatus(oldStatus)) {
			return;
		}
		outbound.sendText(change.chat().id(), msg(MSG_ADMIN_NUDGE));
	}

	private void handleGroupMessage(TelegramMessage message) {
		long chatId = message.chat().id();
		String text = message.text();

		Long pendingUserId = pendingThemeListByChat.get(chatId);
		if (pendingUserId != null && message.from() != null && message.from().id() == pendingUserId) {
			if (text != null && looksLikeSetupCommand(message)) {
				// New /setup supersedes pending wait.
			}
			else {
				pendingThemeListByChat.remove(chatId);
				if (!requireAdmin(message)) {
					return;
				}
				runSetup(chatId, text == null ? "" : text);
				return;
			}
		}

		if (looksLikeSetupCommand(message)) {
			handleSetupCommand(message);
			return;
		}

		Optional<Catalog> configured = catalogStore.findByChatId(chatId);
		if (configured.isPresent()) {
			Catalog catalog = configured.get();
			if (message.messageThreadId() != null && message.messageThreadId() == catalog.inboxThreadId()) {
				handleInboxMessage(message, catalog);
			}
			return;
		}

		if (text != null && !text.isBlank()) {
			outbound.sendText(chatId, msg(MSG_PRE_SETUP));
		}
	}

	private void handleInboxMessage(TelegramMessage message, Catalog catalog) {
		NormalizeResult normalized = inboxNormalizer.normalize(InboxMessageFactsMapper.from(message));
		switch (normalized) {
			case NormalizeResult.Rejected rejected ->
					outbound.replyText(message.chat().id(), message.messageId(), rejected.reason());
			case NormalizeResult.Accepted accepted -> fileAccepted(accepted.draft(), catalog);
		}
	}

	private void fileAccepted(AcceptedDraft draft, Catalog catalog) {
		inboxFiling.file(draft, catalog);
	}

	private void handleSetupCommand(TelegramMessage message) {
		long chatId = message.chat().id();
		if (!requireAdmin(message)) {
			return;
		}
		String remainder = setupRemainder(message.text());
		if (remainder.isBlank()) {
			if (message.from() != null) {
				pendingThemeListByChat.put(chatId, message.from().id());
			}
			outbound.sendText(chatId, msg(MSG_ASK_THEMES));
			return;
		}
		pendingThemeListByChat.remove(chatId);
		runSetup(chatId, remainder);
	}

	private void runSetup(long chatId, String themeListText) {
		CatalogSetupResult result = setupService.setup(chatId, themeListText);
		switch (result) {
			case CatalogSetupResult.NeedsForum ignored -> outbound.sendText(chatId, msg(MSG_NEEDS_FORUM));
			case CatalogSetupResult.EmptyThemeList ignored -> outbound.sendText(chatId, msg(MSG_EMPTY_THEMES));
			case CatalogSetupResult.AlreadyConfigured ignored -> outbound.sendText(chatId, msg(MSG_ALREADY));
			case CatalogSetupResult.Success success -> outbound.sendText(chatId, successText(success.catalog()));
		}
	}

	private boolean requireAdmin(TelegramMessage message) {
		if (message.from() == null) {
			outbound.sendText(message.chat().id(), msg(MSG_NOT_ADMIN));
			return false;
		}
		if (!chatAdminPort.isAdmin(message.chat().id(), message.from().id())) {
			outbound.sendText(message.chat().id(), msg(MSG_NOT_ADMIN));
			return false;
		}
		return true;
	}

	private String successText(Catalog catalog) {
		String themes = catalog.themes().stream().map(ThemeTopic::name).collect(Collectors.joining(", "));
		return messages.getMessage(MSG_SUCCESS, new Object[] {themes}, Locale.ENGLISH);
	}

	private String msg(String code) {
		return messages.getMessage(code, null, Locale.ENGLISH);
	}

	private static boolean isGroupChat(String type) {
		return "group".equals(type) || "supergroup".equals(type);
	}

	private static boolean isAdminStatus(String status) {
		return "administrator".equals(status) || "creator".equals(status);
	}

	/**
	 * UX: {@code /setup AI, Fitness} on one line, or bare {@code /setup} then a follow-up list message.
	 */
	static boolean looksLikeSetupCommand(TelegramMessage message) {
		String text = message.text();
		if (text == null || text.isBlank()) {
			return false;
		}
		if (message.entities() != null) {
			for (TelegramMessageEntity entity : message.entities()) {
				if ("bot_command".equals(entity.type()) && entity.offset() == 0) {
					int end = Math.min(entity.length(), text.length());
					return isSetupToken(text.substring(0, end));
				}
			}
		}
		String firstToken = text.split("\\s+", 2)[0];
		return isSetupToken(firstToken);
	}

	static String setupRemainder(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		String trimmed = text.trim();
		String[] parts = trimmed.split("\\s+", 2);
		if (parts.length == 1) {
			return "";
		}
		return parts[1].trim();
	}

	private static boolean isSetupToken(String token) {
		return "/setup".equals(token) || token.startsWith("/setup@");
	}
}
