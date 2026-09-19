package com.fava.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fava.catalog.SourceType;
import com.fava.classify.ChatModelPort;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatSavedItemEnricherTest {

	@Test
	void llmSuccess_returnsParsedEnrichment() {
		FakeChat chat = new FakeChat("""
				{"source_type":"recipe","title":"Pasta night","recommended_by":"Anna",\
				"tags":["italian","dinner"],"search_text":"A pasta recipe recommended by Anna"}
				""");
		SavedItemEnricher enricher = new ChatSavedItemEnricher(chat);

		SavedItemEnrichment enrichment = enricher.enrich(Optional.empty(), "pasta tip");

		assertThat(enrichment.sourceType()).contains(SourceType.RECIPE);
		assertThat(enrichment.title()).contains("Pasta night");
		assertThat(enrichment.recommendedBy()).contains("Anna");
		assertThat(enrichment.tags()).containsExactly("italian", "dinner");
		assertThat(enrichment.searchText()).contains("A pasta recipe recommended by Anna");
	}

	@Test
	void llmFailure_fallsBackToUrlHeuristicSourceType() {
		FakeChat chat = new FakeChat(new RuntimeException("boom"));
		SavedItemEnricher enricher = new ChatSavedItemEnricher(chat);

		SavedItemEnrichment enrichment = enricher.enrich(
				Optional.of("https://www.instagram.com/p/ABC/"), "caption");

		assertThat(enrichment.sourceType()).contains(SourceType.INSTAGRAM);
		assertThat(enrichment.title()).isEmpty();
		assertThat(enrichment.searchText()).isEmpty();
		assertThat(enrichment.tags()).isEmpty();
	}

	@Test
	void invalidJson_fallsBackToYoutubeHeuristic() {
		FakeChat chat = new FakeChat("not-json");
		SavedItemEnricher enricher = new ChatSavedItemEnricher(chat);

		SavedItemEnrichment enrichment = enricher.enrich(
				Optional.of("https://youtu.be/xyz"), "watch later");

		assertThat(enrichment.sourceType()).contains(SourceType.YOUTUBE);
	}

	@Test
	void unknownSourceTypeFromLlm_prefersUrlHeuristic() {
		FakeChat chat = new FakeChat("""
				{"source_type":"unknown","title":"Post","recommended_by":null,"tags":[],\
				"search_text":"An Instagram post"}
				""");
		SavedItemEnricher enricher = new ChatSavedItemEnricher(chat);

		SavedItemEnrichment enrichment = enricher.enrich(
				Optional.of("https://instagram.com/p/1"), "hi");

		assertThat(enrichment.sourceType()).contains(SourceType.INSTAGRAM);
		assertThat(enrichment.title()).contains("Post");
		assertThat(enrichment.searchText()).contains("An Instagram post");
	}

	private static final class FakeChat implements ChatModelPort {
		private final String response;
		private final RuntimeException error;
		final AtomicReference<String> lastUser = new AtomicReference<>();

		FakeChat(String response) {
			this.response = response;
			this.error = null;
		}

		FakeChat(RuntimeException error) {
			this.response = null;
			this.error = error;
		}

		@Override
		public String complete(String systemPrompt, String userMessage) {
			lastUser.set(userMessage);
			if (error != null) {
				throw error;
			}
			return response;
		}
	}
}
