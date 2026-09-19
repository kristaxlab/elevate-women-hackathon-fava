package com.fava.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fava.catalog.SourceType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UrlSourceTypeHeuristicsTest {

	@Test
	void recognizesInstagramYoutubeLinkedIn() {
		assertThat(UrlSourceTypeHeuristics.fromUrl(Optional.of("https://www.instagram.com/p/x/")))
				.contains(SourceType.INSTAGRAM);
		assertThat(UrlSourceTypeHeuristics.fromUrl(Optional.of("https://www.youtube.com/watch?v=1")))
				.contains(SourceType.YOUTUBE);
		assertThat(UrlSourceTypeHeuristics.fromUrl(Optional.of("https://www.linkedin.com/posts/a")))
				.contains(SourceType.LINKEDIN);
	}

	@Test
	void unknownHostOrMissing_isEmpty() {
		assertThat(UrlSourceTypeHeuristics.fromUrl(Optional.of("https://example.com/a"))).isEmpty();
		assertThat(UrlSourceTypeHeuristics.fromUrl(Optional.empty())).isEmpty();
	}
}
