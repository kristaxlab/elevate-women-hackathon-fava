package com.fava.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InboxMessageNormalizerTest {

	private final InboxMessageNormalizer normalizer = new InboxMessageNormalizer();

	@Test
	void acceptsHttpsUrlInText() {
		InboxMessageFacts facts = facts(
				"Check this https://www.instagram.com/p/ABC123/",
				List.of(),
				false,
				null);

		NormalizeResult result = normalizer.normalize(facts);

		assertThat(result).isInstanceOf(NormalizeResult.Accepted.class);
		AcceptedDraft draft = ((NormalizeResult.Accepted) result).draft();
		assertThat(draft.url()).contains("https://www.instagram.com/p/ABC123/");
		assertThat(draft.bodyText()).isEqualTo("Check this https://www.instagram.com/p/ABC123/");
		assertThat(draft.chatId()).isEqualTo(-100L);
		assertThat(draft.sourceMessageId()).isEqualTo(7L);
	}

	@Test
	void acceptsHttpUrlFromEntity() {
		InboxMessageFacts facts = facts(
				"see link",
				List.of(new ExtractedUrl("http://example.com/a", 0, 4)),
				false,
				null);

		NormalizeResult result = normalizer.normalize(facts);

		assertThat(result).isInstanceOf(NormalizeResult.Accepted.class);
		assertThat(((NormalizeResult.Accepted) result).draft().url()).contains("http://example.com/a");
	}

	@Test
	void acceptsForwardedPostWithTextAndNoUrl() {
		InboxMessageFacts facts = facts(
				"Great tip about running cadence",
				List.of(),
				true,
				null);

		NormalizeResult result = normalizer.normalize(facts);

		assertThat(result).isInstanceOf(NormalizeResult.Accepted.class);
		AcceptedDraft draft = ((NormalizeResult.Accepted) result).draft();
		assertThat(draft.url()).isEmpty();
		assertThat(draft.bodyText()).isEqualTo("Great tip about running cadence");
	}

	@Test
	void rejectsPlainNoteWithoutUrlOrForward() {
		InboxMessageFacts facts = facts("just a note to myself", List.of(), false, null);

		NormalizeResult result = normalizer.normalize(facts);

		assertThat(result).isInstanceOf(NormalizeResult.Rejected.class);
		assertThat(((NormalizeResult.Rejected) result).reason()).isNotBlank();
	}

	@Test
	void rejectsEmptyMessage() {
		InboxMessageFacts facts = facts("  ", List.of(), false, null);

		NormalizeResult result = normalizer.normalize(facts);

		assertThat(result).isInstanceOf(NormalizeResult.Rejected.class);
	}

	@Test
	void rejectsForwardWithNoText() {
		InboxMessageFacts facts = facts(null, List.of(), true, null);

		NormalizeResult result = normalizer.normalize(facts);

		assertThat(result).isInstanceOf(NormalizeResult.Rejected.class);
	}

	@Test
	void prefersFirstUrlFromTextWhenMultiplePresent() {
		InboxMessageFacts facts = facts(
				"a https://one.example/x and https://two.example/y",
				List.of(),
				false,
				null);

		NormalizeResult result = normalizer.normalize(facts);

		assertThat(result).isInstanceOf(NormalizeResult.Accepted.class);
		assertThat(((NormalizeResult.Accepted) result).draft().url()).contains("https://one.example/x");
	}

	private static InboxMessageFacts facts(
			String text, List<ExtractedUrl> urls, boolean forwarded, String forwardOriginText) {
		return new InboxMessageFacts(-100L, 7L, 11L, text, urls, forwarded, forwardOriginText);
	}
}
