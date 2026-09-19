package com.fava;

import com.fava.catalog.CatalogSeeder;
import com.fava.catalog.CatalogStore;
import com.fava.catalog.SavedItemIndexer;
import com.fava.catalog.SavedItemStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional seed-only Catalog loader for manual Smart Search probing ({@code fava.seed.*}).
 */
@Configuration
@EnableConfigurationProperties(SeedProperties.class)
public class SeedConfiguration {

	private static final Logger log = LoggerFactory.getLogger(SeedConfiguration.class);

	@Bean
	ApplicationRunner catalogSeedRunner(
			SeedProperties properties,
			CatalogStore catalogStore,
			SavedItemStore savedItemStore,
			SavedItemIndexer savedItemIndexer) {
		return args -> {
			if (!properties.enabled()) {
				return;
			}
			long chatId = properties.requireChatId();
			CatalogSeeder.Result result =
					new CatalogSeeder(catalogStore, savedItemStore, savedItemIndexer).seed(chatId);
			switch (result) {
				case CatalogSeeder.Result.Seeded seeded ->
						log.info("Catalog seed complete: {} Saved Items for chat {}", seeded.itemCount(), chatId);
				case CatalogSeeder.Result.AlreadySeeded skipped ->
						log.info(
								"Catalog seed skipped (already has {} Saved Items) for chat {}",
								skipped.existingItemCount(),
								chatId);
			}
		};
	}
}
