package com.fava.search;

import com.fava.catalog.EmbeddingModelRegistry;
import com.fava.catalog.EmbeddingSpace;
import com.fava.catalog.NoOpSavedItemIndexer;
import com.fava.catalog.SavedItemEmbeddingStore;
import com.fava.catalog.SavedItemIndexer;
import com.fava.catalog.SavedItemStore;
import com.fava.classify.ChatModelPort;
import com.fava.classify.EmbeddingPort;
import com.fava.classify.OpenAiCompatibleChatModel;
import com.fava.classify.OpenAiCompatibleEmbeddingModel;
import com.fava.classify.OpenRouterProperties;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class SearchConfiguration {

	@Bean
	EmbeddingPort embeddingPort(OpenRouterProperties properties, ObjectMapper objectMapper) {
		if (!properties.hasApiKey()) {
			return text -> {
				throw new IllegalStateException("OpenRouter API key not configured");
			};
		}
		return new OpenAiCompatibleEmbeddingModel(
				properties.apiKey(),
				properties.baseUrl(),
				properties.embeddingModel(),
				objectMapper);
	}

	@Bean
	SavedItemIndexer savedItemIndexer(
			OpenRouterProperties properties,
			SavedItemEmbeddingStore embeddingStore,
			EmbeddingModelRegistry embeddingModelRegistry,
			EmbeddingPort embeddingPort) {
		if (!properties.hasApiKey()) {
			return new NoOpSavedItemIndexer();
		}
		EmbeddingSpace space = EmbeddingSpace.from(properties);
		return new EmbeddingSavedItemIndexer(
				embeddingPort,
				embeddingStore,
				embeddingModelRegistry,
				space.dimensions());
	}

	@Bean
	CatalogSearchPort catalogSearchPort(
			OpenRouterProperties properties,
			SavedItemEmbeddingStore embeddingStore,
			SavedItemStore savedItemStore,
			EmbeddingPort embeddingPort,
			ObjectMapper objectMapper) {
		if (!properties.hasApiKey()) {
			return new KeywordCatalogSearchService(savedItemStore, CatalogSearchService.DEFAULT_TOP_K);
		}
		ChatModelPort chatModel = new OpenAiCompatibleChatModel(
				properties.apiKey(),
				properties.baseUrl(),
				properties.chatModel(),
				objectMapper);
		return new CatalogSearchService(
				embeddingPort,
				embeddingStore,
				savedItemStore,
				chatModel,
				CatalogSearchService.DEFAULT_TOP_K,
				CatalogSearchService.DEFAULT_MAX_DISTANCE);
	}

	@Bean
	ApplicationRunner catalogEmbeddingSyncRunner(
			OpenRouterProperties properties,
			DataSource dataSource,
			EmbeddingModelRegistry embeddingModelRegistry,
			SavedItemEmbeddingStore embeddingStore,
			EmbeddingPort embeddingPort) {
		return args -> {
			if (!properties.hasApiKey()) {
				// Degraded mode: no vectors; registry sync waits until an API key is configured.
				return;
			}
			EmbeddingSpace space = EmbeddingSpace.from(properties);
			new CatalogEmbeddingSync(
					dataSource,
					embeddingModelRegistry,
					embeddingStore,
					embeddingPort,
					space)
					.ensureSynced(space);
		};
	}
}
