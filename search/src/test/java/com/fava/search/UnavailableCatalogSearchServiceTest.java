package com.fava.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnavailableCatalogSearchServiceTest {

	@Test
	void answer_returnsUnavailable_withoutKeywordFallback() {
		CatalogSearchResult result = new UnavailableCatalogSearchService().answer(-100L, "any pilates tips?");

		assertThat(result).isInstanceOf(CatalogSearchResult.Unavailable.class);
		assertThat(((CatalogSearchResult.Unavailable) result).message())
				.isEqualTo(UnavailableCatalogSearchService.AI_UNAVAILABLE_MESSAGE);
		assertThat(((CatalogSearchResult.Unavailable) result).message())
				.containsIgnoringCase("isn't configured");
	}
}
