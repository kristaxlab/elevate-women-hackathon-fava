package com.fava.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fava.classify.ChatModelPort;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StructuredQueryParserTest {

	@Test
	void parsesStructuredQuery_withFiltersAndClampedLimit() {
		RecordingChat chat = new RecordingChat("""
				{"query":"pasta dinner recipes","limit":20,"filters":{"source_type":"recipe",\
				"since":"2024-01-15T00:00:00Z","recommended_by":"Anna","tags":["italian","dinner"],\
				"theme_name":"Food"}}
				""");
		StructuredQueryParser parser = new StructuredQueryParser(chat);

		StructuredQuery parsed = parser.parse("Show me Anna's Italian dinner recipes from Food since mid January");

		assertThat(parsed.query()).isEqualTo("pasta dinner recipes");
		assertThat(parsed.limit()).isEqualTo(10);
		assertThat(parsed.filters().sourceType()).contains("recipe");
		assertThat(parsed.filters().since()).contains(Instant.parse("2024-01-15T00:00:00Z"));
		assertThat(parsed.filters().recommendedBy()).contains("Anna");
		assertThat(parsed.filters().tags()).containsExactly("italian", "dinner");
		assertThat(parsed.filters().themeName()).contains("Food");
		assertThat(chat.lastSystem.get()).containsIgnoringCase("omit");
	}

	@Test
	void omitsUnsetFilters_andDefaultsLimit() {
		RecordingChat chat = new RecordingChat("""
				{"query":"pilates tips","limit":3,"filters":{}}
				""");
		StructuredQueryParser parser = new StructuredQueryParser(chat);

		StructuredQuery parsed = parser.parse("any pilates tips?");

		assertThat(parsed.query()).isEqualTo("pilates tips");
		assertThat(parsed.limit()).isEqualTo(3);
		assertThat(parsed.filters().isEmpty()).isTrue();
	}

	@Test
	void chatFailure_fallsBackToRawQuestionWithDefaultLimit() {
		StructuredQueryParser parser = new StructuredQueryParser((system, user) -> {
			throw new IllegalStateException("boom");
		});

		StructuredQuery parsed = parser.parse("  any pilates tips?  ");

		assertThat(parsed.query()).isEqualTo("any pilates tips?");
		assertThat(parsed.limit()).isEqualTo(StructuredQuery.DEFAULT_LIMIT);
		assertThat(parsed.filters().isEmpty()).isTrue();
	}

	@Test
	void unparsableJson_fallsBackToRawQuestion() {
		StructuredQueryParser parser = new StructuredQueryParser((system, user) -> "not json at all");

		StructuredQuery parsed = parser.parse("find pasta");

		assertThat(parsed.query()).isEqualTo("find pasta");
		assertThat(parsed.limit()).isEqualTo(StructuredQuery.DEFAULT_LIMIT);
		assertThat(parsed.filters().isEmpty()).isTrue();
	}

	private static final class RecordingChat implements ChatModelPort {
		private final String reply;
		final AtomicReference<String> lastSystem = new AtomicReference<>();

		RecordingChat(String reply) {
			this.reply = reply;
		}

		@Override
		public String complete(String systemPrompt, String userMessage) {
			lastSystem.set(systemPrompt);
			return reply;
		}
	}
}
