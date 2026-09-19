package com.fava.telegram;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Long-polls {@code getUpdates} on a background thread when a bot token is configured.
 * Without a token the app still starts; polling is skipped with a warning.
 * Resolves the bot identity (config username and/or {@code getMe}) once before polling.
 */
final class TelegramLongPollingLifecycle implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(TelegramLongPollingLifecycle.class);
	private static final int LONG_POLL_TIMEOUT_SECONDS = 25;

	private final TelegramProperties properties;
	private final TelegramBotClient client;
	private final DmUpdateHandler dmUpdateHandler;
	private final GroupUpdateHandler groupUpdateHandler;
	private final BotUsernameHolder botUsernameHolder;
	private final BotUserIdHolder botUserIdHolder;

	private final Object lifecycleMonitor = new Object();
	private volatile boolean running;
	private Thread pollThread;

	TelegramLongPollingLifecycle(
			TelegramProperties properties,
			TelegramBotClient client,
			DmUpdateHandler dmUpdateHandler,
			GroupUpdateHandler groupUpdateHandler,
			BotUsernameHolder botUsernameHolder,
			BotUserIdHolder botUserIdHolder) {
		this.properties = properties;
		this.client = client;
		this.dmUpdateHandler = dmUpdateHandler;
		this.groupUpdateHandler = groupUpdateHandler;
		this.botUsernameHolder = botUsernameHolder;
		this.botUserIdHolder = botUserIdHolder;
	}

	@Override
	public void start() {
		synchronized (lifecycleMonitor) {
			if (running) {
				return;
			}
			if (!properties.hasToken()) {
				log.warn("fava.telegram.bot-token is empty; skipping Telegram long polling");
				return;
			}
			resolveBotIdentity();
			running = true;
			pollThread = Thread.ofVirtual().name("telegram-long-poll").start(this::pollLoop);
			log.info("Telegram long polling started");
		}
	}

	@Override
	public void stop() {
		synchronized (lifecycleMonitor) {
			running = false;
			if (pollThread != null) {
				pollThread.interrupt();
				pollThread = null;
			}
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private void resolveBotIdentity() {
		if (properties.hasConfiguredUsername()) {
			botUsernameHolder.set(properties.botUsername());
			log.info("Using configured Telegram bot username @{}", botUsernameHolder.get());
		}
		try {
			TelegramBotClient.BotIdentity me = client.getMe();
			botUserIdHolder.set(me.id());
			if (!properties.hasConfiguredUsername()) {
				botUsernameHolder.set(me.username());
				if (me.username() != null) {
					log.info("Resolved Telegram bot username via getMe: @{}", me.username());
				}
				else {
					log.warn("getMe returned no username; DM Catalog deep links will be omitted");
				}
			}
			log.info("Resolved Telegram bot user id via getMe: {}", me.id());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("getMe interrupted; bot identity incomplete");
		}
		catch (IOException e) {
			log.warn("getMe failed; bot identity incomplete: {}", e.toString());
		}
	}

	private void pollLoop() {
		long offset = 0L;
		while (running) {
			try {
				List<TelegramUpdate> updates = client.getUpdates(offset, LONG_POLL_TIMEOUT_SECONDS);
				for (TelegramUpdate update : updates) {
					dmUpdateHandler.handle(update);
					groupUpdateHandler.handle(update);
					if (update.updateId() != null) {
						offset = update.updateId() + 1;
					}
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
			catch (IOException e) {
				if (!running) {
					break;
				}
				log.warn("Telegram getUpdates failed: {}", e.toString());
				sleepQuietly(2_000L);
			}
			catch (RuntimeException e) {
				if (!running) {
					break;
				}
				log.warn("Telegram poll loop error: {}", e.toString());
				sleepQuietly(2_000L);
			}
		}
		running = false;
		log.info("Telegram long polling stopped");
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
