package com.fava.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CatalogSeedCorpusTest {

	@Test
	void corpus_hasAboutThirtyItemsWithEnrichmentAndLibraryPointers() {
		List<CatalogSeedCorpus.SeedDraft> items = CatalogSeedCorpus.items();

		assertThat(items).hasSizeBetween(28, 35);
		assertThat(items).allSatisfy(item -> {
			assertThat(item.bodyText()).isNotBlank();
			assertThat(item.themeName()).isNotBlank();
			assertThat(item.title()).isNotBlank();
			assertThat(item.searchText()).isNotBlank();
			assertThat(item.searchText()).doesNotMatch(".*[\\u0400-\\u04FF].*");
			assertThat(item.sourceType()).isNotNull();
			assertThat(item.tags()).isNotEmpty();
			assertThat(item.userLibItemId()).isNotBlank();
		});
	}

	@Test
	void corpus_spreadsThemesSourceTypesTagsAndRecommendations() {
		List<CatalogSeedCorpus.SeedDraft> items = CatalogSeedCorpus.items();

		assertThat(items.stream().map(CatalogSeedCorpus.SeedDraft::themeName).collect(Collectors.toSet()))
				.hasSizeGreaterThanOrEqualTo(3);
		assertThat(items.stream().map(CatalogSeedCorpus.SeedDraft::sourceType).collect(Collectors.toSet()))
				.hasSizeGreaterThanOrEqualTo(5);
		assertThat(items.stream().flatMap(i -> i.tags().stream()).collect(Collectors.toSet()))
				.hasSizeGreaterThanOrEqualTo(8);
		assertThat(items.stream().flatMap(i -> i.recommendedBy().stream()).collect(Collectors.toSet()))
				.hasSizeGreaterThanOrEqualTo(3);
	}

	@Test
	void corpus_includesRussianBodiesAndUsefulExternalUrls() {
		List<CatalogSeedCorpus.SeedDraft> items = CatalogSeedCorpus.items();

		long russianBodies = items.stream().filter(i -> containsCyrillic(i.bodyText())).count();
		assertThat(russianBodies).isBetween(8L, 12L);

		Set<String> hosts = new HashSet<>();
		items.stream()
				.flatMap(i -> i.url().stream())
				.forEach(url -> {
					String lower = url.toLowerCase(Locale.ROOT);
					if (lower.contains("instagram.com")) {
						hosts.add("instagram");
					}
					if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
						hosts.add("youtube");
					}
				});
		assertThat(hosts).contains("instagram", "youtube");
	}

	private static boolean containsCyrillic(String text) {
		return text.codePoints().anyMatch(cp -> Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.CYRILLIC);
	}
}
