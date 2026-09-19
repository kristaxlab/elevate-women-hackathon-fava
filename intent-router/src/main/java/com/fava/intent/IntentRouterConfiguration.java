package com.fava.intent;

import com.fava.classify.ChatModelPort;
import com.fava.classify.OpenAiCompatibleChatModel;
import com.fava.classify.OpenRouterProperties;
import com.fava.ingest.InboxFilingService;
import com.fava.ingest.InboxMessageNormalizer;
import com.fava.search.CatalogSearchPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class IntentRouterConfiguration {

	@Bean
	ActionIntentClassifier actionIntentClassifier(OpenRouterProperties properties, ObjectMapper objectMapper) {
		if (!properties.hasApiKey()) {
			return new HeuristicActionIntentClassifier();
		}
		ChatModelPort chat = new OpenAiCompatibleChatModel(
				properties.apiKey(),
				properties.baseUrl(),
				properties.chatModel(),
				objectMapper);
		return new ChatActionIntentClassifier(chat);
	}

	@Bean
	IntentRouter intentRouter(
			ActionIntentClassifier actionIntentClassifier,
			InboxMessageNormalizer inboxMessageNormalizer,
			InboxFilingService inboxFilingService,
			CatalogSearchPort catalogSearchPort,
			ParticipantNotifyPort participantNotifyPort) {
		return new IntentRouter(
				actionIntentClassifier,
				inboxMessageNormalizer,
				inboxFilingService,
				catalogSearchPort,
				participantNotifyPort);
	}
}
