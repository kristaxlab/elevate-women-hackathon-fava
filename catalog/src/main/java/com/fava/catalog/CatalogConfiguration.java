package com.fava.catalog;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogConfiguration {

	@Bean
	CatalogStore catalogStore(DataSource dataSource) {
		CatalogSchema.ensure(dataSource);
		return new JdbcCatalogStore(dataSource);
	}

	@Bean
	SavedItemStore savedItemStore(DataSource dataSource) {
		CatalogSchema.ensure(dataSource);
		return new JdbcSavedItemStore(dataSource);
	}
}
