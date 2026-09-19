package com.fava.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CatalogAnswerFormatterTest {

	@Test
	void formatsAnswerWithCitationBulletsAndUrls() {
		String text = CatalogAnswerFormatter.format(new CatalogSearchResult.Answer(
				"You saved a pilates tip.",
				List.of(new CatalogSearchResult.Citation(
						"Pilates reformer tip",
						Optional.of("https://example.com/pilates")))));

		assertThat(text).isEqualTo("""
				You saved a pilates tip.

				Citations:
				• Pilates reformer tip
				  https://example.com/pilates""".stripIndent().trim());
	}

	@Test
	void formatsNothingFoundAsPlainMessage() {
		assertThat(CatalogAnswerFormatter.format(
						new CatalogSearchResult.NothingFound(CatalogSearchService.NOTHING_FOUND_MESSAGE)))
				.isEqualTo(CatalogSearchService.NOTHING_FOUND_MESSAGE);
	}
}
