package com.fava.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fava.classify.OpenRouterProperties;
import org.junit.jupiter.api.Test;

class EmbeddingSpaceTest {

	@Test
	void defaultSpace_matchesOpenRouterPropertyDefaults() {
		OpenRouterProperties defaults = new OpenRouterProperties(null, null, null, null, null);
		assertThat(EmbeddingSpace.from(defaults)).isEqualTo(EmbeddingSpace.DEFAULT);
	}
}
