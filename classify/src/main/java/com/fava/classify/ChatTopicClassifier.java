package com.fava.classify;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Classifier Decision via chat model. Expects JSON
 * {@code {"theme":"<name>","confidence":0.0-1.0}}; low confidence, unknown theme,
 * or parse failure → {@link ClassifierDecision.NeedsUserPick}.
 */
public final class ChatTopicClassifier implements TopicClassifier {

	private static final Logger log = LoggerFactory.getLogger(ChatTopicClassifier.class);

	private final ChatModelPort chat;
	private final double confidenceThreshold;
	private final ObjectMapper objectMapper;

	public ChatTopicClassifier(ChatModelPort chat, double confidenceThreshold) {
		this(chat, confidenceThreshold, new ObjectMapper());
	}

	ChatTopicClassifier(ChatModelPort chat, double confidenceThreshold, ObjectMapper objectMapper) {
		this.chat = chat;
		this.confidenceThreshold = confidenceThreshold;
		this.objectMapper = objectMapper;
	}

	@Override
	public ClassifierDecision classify(String draftText, List<String> themeNames) {
		if (themeNames == null || themeNames.isEmpty()) {
			return new ClassifierDecision.NeedsUserPick();
		}
		String system = buildSystemPrompt(themeNames);
		String user = draftText == null ? "" : draftText;
		String raw;
		try {
			raw = chat.complete(system, user);
		}
		catch (RuntimeException e) {
			log.warn("Chat model failed during classify: {}", e.toString());
			return new ClassifierDecision.NeedsUserPick();
		}
		return parseDecision(raw, themeNames);
	}

	private ClassifierDecision parseDecision(String raw, List<String> themeNames) {
		Optional<JsonNode> json = extractJson(raw);
		if (json.isEmpty()) {
			return new ClassifierDecision.NeedsUserPick();
		}
		JsonNode node = json.get();
		String theme = textOrNull(node, "theme");
		Double confidence = numberOrNull(node, "confidence");
		if (theme == null || confidence == null) {
			return new ClassifierDecision.NeedsUserPick();
		}
		Optional<String> matched = matchTheme(theme, themeNames);
		if (matched.isEmpty()) {
			return new ClassifierDecision.NeedsUserPick();
		}
		if (confidence < confidenceThreshold) {
			return new ClassifierDecision.NeedsUserPick();
		}
		return new ClassifierDecision.Confident(matched.get());
	}

	private Optional<JsonNode> extractJson(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String trimmed = stripMarkdownFence(raw.trim());
		try {
			return Optional.of(objectMapper.readTree(trimmed));
		}
		catch (Exception ignored) {
			int start = trimmed.indexOf('{');
			int end = trimmed.lastIndexOf('}');
			if (start >= 0 && end > start) {
				try {
					return Optional.of(objectMapper.readTree(trimmed.substring(start, end + 1)));
				}
				catch (Exception e) {
					log.debug("Could not parse classifier JSON: {}", e.toString());
				}
			}
			return Optional.empty();
		}
	}

	private static String stripMarkdownFence(String text) {
		if (!text.startsWith("```")) {
			return text;
		}
		int firstNl = text.indexOf('\n');
		if (firstNl < 0) {
			return text;
		}
		String body = text.substring(firstNl + 1);
		int fence = body.lastIndexOf("```");
		if (fence >= 0) {
			body = body.substring(0, fence);
		}
		return body.trim();
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull() || !value.isTextual()) {
			return null;
		}
		String text = value.asText();
		return text == null || text.isBlank() ? null : text.trim();
	}

	private static Double numberOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull() || !value.isNumber()) {
			return null;
		}
		return value.asDouble();
	}

	private static Optional<String> matchTheme(String theme, List<String> themeNames) {
		for (String name : themeNames) {
			if (name.equalsIgnoreCase(theme)) {
				return Optional.of(name);
			}
		}
		return Optional.empty();
	}

	static String buildSystemPrompt(List<String> themeNames) {
		return """
				You assign exactly one Theme Topic for a saved item draft.
				Choose only from this list: %s
				Reply with JSON only, no prose: {"theme":"<exact name from list>","confidence":<0.0-1.0>}
				confidence is how sure you are that this is the single best Theme Topic.
				Never invent themes. Never use Inbox or Smart Search.
				""".formatted(String.join(", ", themeNames)).trim();
	}
}
