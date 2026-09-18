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
	DmUpdateHandler dmUpdateHandler(MessageSource messageSource, TelegramBotClient telegramBotClient) {
		return new DmUpdateHandler(messageSource, telegramBotClient);
	}

	@Bean
	TelegramLongPollingLifecycle telegramLongPollingLifecycle(
			TelegramProperties properties,
			TelegramBotClient telegramBotClient,
			DmUpdateHandler dmUpdateHandler) {
		return new TelegramLongPollingLifecycle(properties, telegramBotClient, dmUpdateHandler);
	}
}
