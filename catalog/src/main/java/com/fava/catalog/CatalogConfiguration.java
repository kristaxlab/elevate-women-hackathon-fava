package com.fava.catalog;

import com.fava.classify.EmbeddingPort;
import com.fava.classify.OpenRouterProperties;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class CatalogConfiguration {

	@Bean
	CatalogStore catalogStore(DataSource dataSource, OpenRouterProperties openRouter) {
		CatalogSchema.ensure(dataSource, EmbeddingSpace.from(openRouter));
		return new JdbcCatalogStore(dataSource);
	}

	@Bean
	SavedItemStore savedItemStore(DataSource dataSource, OpenRouterProperties openRouter) {
		CatalogSchema.ensure(dataSource, EmbeddingSpace.from(openRouter));
		return new JdbcSavedItemStore(dataSource);
	}

	@Bean
	EmbeddingModelRegistry embeddingModelRegistry(DataSource dataSource, OpenRouterProperties openRouter) {
		CatalogSchema.ensure(dataSource, EmbeddingSpace.from(openRouter));
		return new JdbcEmbeddingModelRegistry(dataSource);
	}

	@Bean
	SavedItemEmbeddingStore savedItemEmbeddingStore(DataSource dataSource, OpenRouterProperties openRouter) {
		EmbeddingSpace space = EmbeddingSpace.from(openRouter);
		CatalogSchema.ensure(dataSource, space);
		return new JdbcSavedItemEmbeddingStore(dataSource, space);
	}

	@Bean
	TransactionTemplate catalogTransactionTemplate(DataSource dataSource) {
		return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
	}

	@Bean
	CatalogItemCreator catalogItemCreator(
			CatalogStore catalogStore,
			SavedItemStore savedItemStore,
			SavedItemEmbeddingStore savedItemEmbeddingStore,
			EmbeddingModelRegistry embeddingModelRegistry,
			EmbeddingPort embeddingPort,
			TransactionTemplate catalogTransactionTemplate) {
		return new CatalogItemCreator(
				catalogStore,
				savedItemStore,
				savedItemEmbeddingStore,
				embeddingModelRegistry,
				embeddingPort,
				catalogTransactionTemplate);
	}
}
