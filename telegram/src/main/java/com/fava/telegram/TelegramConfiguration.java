package com.fava.telegram;

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
	BotUsernameHolder botUsernameHolder(TelegramProperties properties) {
		BotUsernameHolder holder = new BotUsernameHolder();
		if (properties.hasConfiguredUsername()) {
			holder.set(properties.botUsername());
		}
		return holder;
	}

	@Bean
	DmUpdateHandler dmUpdateHandler(
			MessageSource messageSource,
			TelegramBotClient telegramBotClient,
			BotUsernameHolder botUsernameHolder) {
		return new DmUpdateHandler(messageSource, telegramBotClient, botUsernameHolder::get);
	}

	@Bean
	TelegramLongPollingLifecycle telegramLongPollingLifecycle(
			TelegramProperties properties,
			TelegramBotClient telegramBotClient,
			DmUpdateHandler dmUpdateHandler,
			BotUsernameHolder botUsernameHolder) {
		return new TelegramLongPollingLifecycle(
				properties, telegramBotClient, dmUpdateHandler, botUsernameHolder);
	}
}
