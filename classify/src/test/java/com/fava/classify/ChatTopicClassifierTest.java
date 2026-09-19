package com.fava.classify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatTopicClassifierTest {

	@Test
	void confidentJson_returnsChosenTheme() {
		FakeChatModel chat = new FakeChatModel("""
				{"theme":"Fitness","confidence":0.91}
				""");
		TopicClassifier classifier = new ChatTopicClassifier(chat, 0.7);

		ClassifierDecision decision = classifier.classify(
				"https://example.com/hiit intervals workout",
				List.of("AI", "Fitness"));

		assertThat(decision).isEqualTo(new ClassifierDecision.Confident("Fitness"));
		assertThat(chat.lastUserMessage.get()).contains("https://example.com/hiit");
		assertThat(chat.lastSystemPrompt.get()).contains("AI");
		assertThat(chat.lastSystemPrompt.get()).contains("Fitness");
	}

	@Test
	void confidenceBelowThreshold_needsUserPick() {
		FakeChatModel chat = new FakeChatModel("""
				{"theme":"AI","confidence":0.4}
				""");
		TopicClassifier classifier = new ChatTopicClassifier(chat, 0.7);

		ClassifierDecision decision = classifier.classify("ambiguous note", List.of("AI", "Fitness"));

		assertThat(decision).isEqualTo(new ClassifierDecision.NeedsUserPick());
	}

	@Test
	void unknownThemeName_needsUserPick() {
		FakeChatModel chat = new FakeChatModel("""
				{"theme":"Inbox","confidence":0.99}
				""");
		TopicClassifier classifier = new ChatTopicClassifier(chat, 0.7);

		ClassifierDecision decision = classifier.classify("something", List.of("AI", "Fitness"));

		assertThat(decision).isEqualTo(new ClassifierDecision.NeedsUserPick());
	}

	@Test
	void unparseableModelOutput_needsUserPick() {
		FakeChatModel chat = new FakeChatModel("sorry I cannot help");
		TopicClassifier classifier = new ChatTopicClassifier(chat, 0.7);

		ClassifierDecision decision = classifier.classify("link text", List.of("AI"));

		assertThat(decision).isEqualTo(new ClassifierDecision.NeedsUserPick());
	}

	@Test
	void emptyThemeList_needsUserPickWithoutCallingChat() {
		FakeChatModel chat = new FakeChatModel("""
				{"theme":"AI","confidence":0.99}
				""");
		TopicClassifier classifier = new ChatTopicClassifier(chat, 0.7);

		ClassifierDecision decision = classifier.classify("anything", List.of());

		assertThat(decision).isEqualTo(new ClassifierDecision.NeedsUserPick());
		assertThat(chat.calls).isEmpty();
	}

	@Test
	void markdownFencedJson_isParsed() {
		FakeChatModel chat = new FakeChatModel("""
				```json
				{"theme":"AI","confidence":0.88}
				```
				""");
		TopicClassifier classifier = new ChatTopicClassifier(chat, 0.7);

		assertThat(classifier.classify("ml paper", List.of("AI", "Fitness")))
				.isEqualTo(new ClassifierDecision.Confident("AI"));
	}

	private static final class FakeChatModel implements ChatModelPort {
		private final String response;
		final List<String> calls = new ArrayList<>();
		final AtomicReference<String> lastSystemPrompt = new AtomicReference<>();
		final AtomicReference<String> lastUserMessage = new AtomicReference<>();

		FakeChatModel(String response) {
			this.response = response;
		}

		@Override
		public String complete(String systemPrompt, String userMessage) {
			calls.add(userMessage);
			lastSystemPrompt.set(systemPrompt);
			lastUserMessage.set(userMessage);
			return response;
		}
	}
}
