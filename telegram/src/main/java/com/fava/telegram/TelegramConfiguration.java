package com.fava.telegram;

import com.fava.catalog.CatalogSetupService;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.DefaultCatalogSetupService;
import com.fava.catalog.SavedItemIndexer;
import com.fava.catalog.SavedItemStore;
import com.fava.classify.TopicClassifier;
import com.fava.ingest.InboxFilingService;
import com.fava.ingest.InboxMessageNormalizer;
import com.fava.search.CatalogSearchPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(TelegramProperties.class)
public class TelegramConfiguration {

	@Bean
	TelegramBotClient telegramBotClient(TelegramProperties properties, ObjectMapper objectMapper) {
		return new TelegramBotClient(properties.botToken() == null ? "" : properties.botToken(), objectMapper);
	}

	@Bean
	CatalogSetupService catalogSetupService(CatalogStore catalogStore, TelegramBotClient telegramBotClient) {
		return new DefaultCatalogSetupService(catalogStore, telegramBotClient);
	}

	@Bean
	InboxMessageNormalizer inboxMessageNormalizer() {
		return new InboxMessageNormalizer();
	}

	@Bean
	InboxFilingService inboxFilingService(
			SavedItemStore savedItemStore,
			TelegramBotClient telegramBotClient,
			TopicClassifier topicClassifier,
			SavedItemIndexer savedItemIndexer) {
		return new InboxFilingService(savedItemStore, telegramBotClient, topicClassifier, savedItemIndexer);
	}

	@Bean
	BotUsernameHolder botUsernameHolder(TelegramProperties properties) {
		BotUsernameHolder holder = new BotUsernameHolder();
		if (properties.hasConfiguredUsername()) {
			holder.set(properties.botUsername());
		}
		return holder;
	}

	@Bean
	BotUserIdHolder botUserIdHolder() {
		return new BotUserIdHolder();
	}

	@Bean
	DmUpdateHandler dmUpdateHandler(
			MessageSource messageSource,
			TelegramBotClient telegramBotClient,
			BotUsernameHolder botUsernameHolder) {
		return new DmUpdateHandler(messageSource, telegramBotClient, botUsernameHolder::get);
	}

	@Bean
	GroupUpdateHandler groupUpdateHandler(
			MessageSource messageSource,
			TelegramBotClient telegramBotClient,
			CatalogStore catalogStore,
			CatalogSetupService catalogSetupService,
			BotUserIdHolder botUserIdHolder,
			InboxMessageNormalizer inboxMessageNormalizer,
			InboxFilingService inboxFilingService,
			CatalogSearchPort catalogSearchPort) {
		return new GroupUpdateHandler(
				messageSource,
				telegramBotClient,
				catalogStore,
				catalogSetupService,
				telegramBotClient,
				botUserIdHolder::get,
				inboxMessageNormalizer,
				inboxFilingService,
				catalogSearchPort);
	}

	@Bean
	TelegramLongPollingLifecycle telegramLongPollingLifecycle(
			TelegramProperties properties,
			TelegramBotClient telegramBotClient,
			DmUpdateHandler dmUpdateHandler,
			GroupUpdateHandler groupUpdateHandler,
			BotUsernameHolder botUsernameHolder,
			BotUserIdHolder botUserIdHolder) {
		return new TelegramLongPollingLifecycle(
				properties,
				telegramBotClient,
				dmUpdateHandler,
				groupUpdateHandler,
				botUsernameHolder,
				botUserIdHolder);
	}
}
