package com.fava.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CatalogAnswerFormatterTest {

	@Test
	void formatsRankedListWithThemeTopicDeepLinks() {
		String text = CatalogAnswerFormatter.format(new CatalogSearchResult.Answer(
				"Here are 1 matching saves.",
				List.of(new CatalogSearchResult.Citation(
						"Pilates reformer tip",
						Optional.of("article"),
						Optional.of("https://example.com/pilates"),
						Optional.of("https://t.me/c/777/9001")))));

		assertThat(text).isEqualTo("""
				Here are 1 matching saves.

				1. Pilates reformer tip (article)
				   https://t.me/c/777/9001""".stripIndent().trim());
	}

	@Test
	void prefersThemeTopicLinkOverUrl() {
		String text = CatalogAnswerFormatter.format(new CatalogSearchResult.Answer(
				"Found it.",
				List.of(new CatalogSearchResult.Citation(
						"Tip",
						Optional.empty(),
						Optional.of("https://example.com/x"),
						Optional.of("https://t.me/c/1/2")))));

		assertThat(text).contains("https://t.me/c/1/2");
		assertThat(text).doesNotContain("https://example.com/x");
	}

	@Test
	void formatsNothingFoundAsPlainMessage() {
		assertThat(CatalogAnswerFormatter.format(
						new CatalogSearchResult.NothingFound(CatalogSearchService.NOTHING_FOUND_MESSAGE)))
				.isEqualTo(CatalogSearchService.NOTHING_FOUND_MESSAGE);
	}

	@Test
	void formatsUnavailableAsPlainMessage() {
		assertThat(CatalogAnswerFormatter.format(
						new CatalogSearchResult.Unavailable(UnavailableCatalogSearchService.AI_UNAVAILABLE_MESSAGE)))
				.isEqualTo(UnavailableCatalogSearchService.AI_UNAVAILABLE_MESSAGE);
	}
}
