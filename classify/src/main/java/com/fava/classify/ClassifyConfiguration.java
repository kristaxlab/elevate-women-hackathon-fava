package com.fava.classify;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class ClassifyConfiguration {

	static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.7;

	@Bean
	TopicClassifier topicClassifier(OpenRouterProperties properties, ObjectMapper objectMapper) {
		if (!properties.hasApiKey()) {
			return new FirstThemeTopicClassifier();
		}
		ChatModelPort chat = new OpenAiCompatibleChatModel(
				properties.apiKey(),
				properties.baseUrl(),
				properties.chatModel(),
				objectMapper);
		return new ChatTopicClassifier(chat, DEFAULT_CONFIDENCE_THRESHOLD);
	}
}
