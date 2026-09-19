package com.fava.catalog;

import com.fava.classify.OpenRouterProperties;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
