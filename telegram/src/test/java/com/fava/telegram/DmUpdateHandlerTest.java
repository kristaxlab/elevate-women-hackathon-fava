package com.fava.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

class DmUpdateHandlerTest {

	private static final String WELCOME = "Welcome to Fava — your personal Catalog bot.";
	private static final String FALLBACK = "Send /start to learn what Fava can do.";
	private static final String BOT_USERNAME = "fava_test_bot";
	/**
	 * Telegram startgroup admin= flags for forum Catalog setup (manage_topics required;
	 * change_info / delete_messages / restrict_members / pin_messages commonly requested for admin bots).
	 */
	private static final String ADMIN =
			"change_info+delete_messages+restrict_members+pin_messages+manage_topics";
	private static final String CREATE_URL =
			"https://t.me/" + BOT_USERNAME + "?startgroup=create&admin=" + ADMIN;
	private static final String EXISTING_URL =
			"https://t.me/" + BOT_USERNAME + "?startgroup=existing&admin=" + ADMIN;

	private RecordingOutbound outbound;
	private DmUpdateHandler handler;

	@BeforeEach
	void setUp() {
		outbound = new RecordingOutbound();
		StaticMessageSource messages = new StaticMessageSource();
		messages.addMessage("fava.dm.start", Locale.ENGLISH, WELCOME);
		messages.addMessage("fava.dm.fallback", Locale.ENGLISH, FALLBACK);
		handler = new DmUpdateHandler(messages, outbound, () -> BOT_USERNAME);
	}

	@Test
	void privateStartCommand_sendsWelcomeWithCreateAndExistingGroupLinks() {
		TelegramUpdate update = privateTextUpdate("/start", List.of(botCommand(0, 6)));

		handler.handle(update);

		assertThat(outbound.keyboardSent).hasSize(1);
		RecordingOutbound.KeyboardSent sent = outbound.keyboardSent.getFirst();
		assertThat(sent.chatId()).isEqualTo(42L);
		assertThat(sent.text()).isEqualTo(WELCOME);
		assertThat(sent.buttons()).hasSize(2);

		InlineUrlButton create = sent.buttons().get(0);
		InlineUrlButton existing = sent.buttons().get(1);
		assertThat(create.text()).isEqualTo("Create my catalog");
		assertThat(existing.text()).isEqualTo("I already have a group");
		assertThat(create.url()).isEqualTo(CREATE_URL);
		assertThat(existing.url()).isEqualTo(EXISTING_URL);
		assertThat(create.url()).contains("manage_topics");
		assertThat(existing.url()).contains("manage_topics");
		assertThat(outbound.plainSent).isEmpty();
	}

	@Test
	void privateUnknownText_sendsFallbackPointingToStart() {
		TelegramUpdate update = privateTextUpdate("hello there", List.of());

		handler.handle(update);

		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(42L, FALLBACK));
		assertThat(outbound.keyboardSent).isEmpty();
	}

	@Test
	void groupChatMessage_isNoOp() {
		TelegramUpdate update = new TelegramUpdate(
				2L,
				new TelegramMessage(
						11L,
						new TelegramChat(99L, "supergroup"),
						"/start",
						List.of(botCommand(0, 6))));

		handler.handle(update);

		assertThat(outbound.plainSent).isEmpty();
		assertThat(outbound.keyboardSent).isEmpty();
	}

	@Test
	void updateWithoutMessage_isNoOp() {
		handler.handle(new TelegramUpdate(3L, null));

		assertThat(outbound.plainSent).isEmpty();
		assertThat(outbound.keyboardSent).isEmpty();
	}

	@Test
	void privateStartCommand_withoutBotUsername_sendsWelcomeTextOnly() {
		StaticMessageSource messages = new StaticMessageSource();
		messages.addMessage("fava.dm.start", Locale.ENGLISH, WELCOME);
		messages.addMessage("fava.dm.fallback", Locale.ENGLISH, FALLBACK);
		handler = new DmUpdateHandler(messages, outbound, () -> null);

		handler.handle(privateTextUpdate("/start", List.of(botCommand(0, 6))));

		assertThat(outbound.plainSent).containsExactly(new RecordingOutbound.PlainSent(42L, WELCOME));
		assertThat(outbound.keyboardSent).isEmpty();
	}

	private static TelegramUpdate privateTextUpdate(String text, List<TelegramMessageEntity> entities) {
		return new TelegramUpdate(
				1L,
				new TelegramMessage(
						10L,
						new TelegramChat(42L, "private"),
						text,
						entities));
	}

	private static TelegramMessageEntity botCommand(int offset, int length) {
		return new TelegramMessageEntity("bot_command", offset, length);
	}

	private static final class RecordingOutbound implements TelegramOutbound {
		final java.util.List<PlainSent> plainSent = new java.util.ArrayList<>();
		final java.util.List<KeyboardSent> keyboardSent = new java.util.ArrayList<>();

		@Override
		public void sendText(long chatId, String text) {
			plainSent.add(new PlainSent(chatId, text));
		}

		@Override
		public void sendTextWithInlineKeyboard(long chatId, String text, List<InlineUrlButton> buttons) {
			keyboardSent.add(new KeyboardSent(chatId, text, List.copyOf(buttons)));
		}

		@Override
		public void replyText(long chatId, long replyToMessageId, String text) {
		}

		@Override
		public void copyMessage(long chatId, long fromMessageId, long toMessageThreadId) {
		}

		record PlainSent(long chatId, String text) {
		}

		record KeyboardSent(long chatId, String text, List<InlineUrlButton> buttons) {
		}
	}
}
