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

	private RecordingOutbound outbound;
	private DmUpdateHandler handler;

	@BeforeEach
	void setUp() {
		outbound = new RecordingOutbound();
		StaticMessageSource messages = new StaticMessageSource();
		messages.addMessage("fava.dm.start", Locale.ENGLISH, WELCOME);
		messages.addMessage("fava.dm.fallback", Locale.ENGLISH, FALLBACK);
		handler = new DmUpdateHandler(messages, outbound);
	}

	@Test
	void privateStartCommand_sendsWelcomeFromMessageSource() {
		TelegramUpdate update = privateTextUpdate("/start", List.of(botCommand(0, 6)));

		handler.handle(update);

		assertThat(outbound.sent).containsExactly(new RecordingOutbound.Sent(42L, WELCOME));
	}

	@Test
	void privateUnknownText_sendsFallbackPointingToStart() {
		TelegramUpdate update = privateTextUpdate("hello there", List.of());

		handler.handle(update);

		assertThat(outbound.sent).containsExactly(new RecordingOutbound.Sent(42L, FALLBACK));
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

		assertThat(outbound.sent).isEmpty();
	}

	@Test
	void updateWithoutMessage_isNoOp() {
		handler.handle(new TelegramUpdate(3L, null));

		assertThat(outbound.sent).isEmpty();
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
		final java.util.List<Sent> sent = new java.util.ArrayList<>();

		@Override
		public void sendText(long chatId, String text) {
			sent.add(new Sent(chatId, text));
		}

		record Sent(long chatId, String text) {
		}
	}
}
