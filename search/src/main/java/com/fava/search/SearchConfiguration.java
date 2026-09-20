package com.fava.search;

import com.fava.catalog.CatalogStore;
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
			StructuredItemsSearcher structuredItemsSearcher,
			StructuredQueryParser structuredQueryParser,
			ObjectMapper objectMapper) {
		if (!properties.hasApiKey()) {
			return new UnavailableCatalogSearchService();
		}
		ChatModelPort chatModel = new OpenAiCompatibleChatModel(
				properties.apiKey(),
				properties.baseUrl(),
				properties.chatModel(),
				objectMapper);
		return new CatalogSearchService(
				structuredQueryParser,
				structuredItemsSearcher,
				chatModel,
				CatalogSearchService.DEFAULT_MAX_DISTANCE);
	}

	@Bean
	StructuredQueryParser structuredQueryParser(OpenRouterProperties properties, ObjectMapper objectMapper) {
		ChatModelPort chatModel;
		if (!properties.hasApiKey()) {
			chatModel = (systemPrompt, userMessage) -> {
				throw new IllegalStateException("OpenRouter API key not configured");
			};
		}
		else {
			chatModel = new OpenAiCompatibleChatModel(
					properties.apiKey(),
					properties.baseUrl(),
					properties.chatModel(),
					objectMapper);
		}
		return new StructuredQueryParser(chatModel, objectMapper);
	}

	@Bean
	StructuredItemsSearcher structuredItemsSearcher(
			CatalogStore catalogStore,
			SavedItemStore savedItemStore,
			SavedItemEmbeddingStore embeddingStore,
			EmbeddingPort embeddingPort) {
		return new StructuredItemsSearcher(catalogStore, savedItemStore, embeddingStore, embeddingPort);
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
				// No API key: skip vector sync; Smart Search replies unavailable until configured.
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
