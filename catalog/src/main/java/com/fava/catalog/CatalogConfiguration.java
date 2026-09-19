package com.fava.catalog;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogConfiguration {

	@Value("${fava.openrouter.embedding-dimensions:1536}")
	private int embeddingDimensions;

	@Bean
	CatalogStore catalogStore(DataSource dataSource) {
		CatalogSchema.ensure(dataSource, embeddingDimensions);
		return new JdbcCatalogStore(dataSource);
	}

	@Bean
	SavedItemStore savedItemStore(DataSource dataSource) {
		CatalogSchema.ensure(dataSource, embeddingDimensions);
		return new JdbcSavedItemStore(dataSource);
	}

	@Bean
	EmbeddingModelRegistry embeddingModelRegistry(DataSource dataSource) {
		CatalogSchema.ensure(dataSource, embeddingDimensions);
		return new JdbcEmbeddingModelRegistry(dataSource);
	}

	@Bean
	SavedItemEmbeddingStore savedItemEmbeddingStore(DataSource dataSource) {
		CatalogSchema.ensure(dataSource, embeddingDimensions);
		return new JdbcSavedItemEmbeddingStore(dataSource, embeddingDimensions);
	}
}
