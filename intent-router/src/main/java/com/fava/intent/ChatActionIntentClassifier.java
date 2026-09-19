package com.fava.intent;

import com.fava.classify.ChatModelPort;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Action Intent via chat model. Expects JSON {@code {"intent":"save"|"search"|"unclear"}}.
 * Model/HTTP failures throw {@link ActionIntentClassificationException} (fail closed).
 */
public final class ChatActionIntentClassifier implements ActionIntentClassifier {

	private static final Logger log = LoggerFactory.getLogger(ChatActionIntentClassifier.class);

	private static final String SYSTEM_PROMPT = """
			You classify what a Telegram catalog participant wants to do with one message.
			Choose exactly one Action Intent:
			- save: they want to file a link, forward, or save candidate into the catalog
			- search: they want a natural-language answer from their saved catalog
			- unclear: greeting, thanks, dual actions, or anything not clearly save or search
			A question that includes a URL can still be search (the URL is question context, not a save).
			Reply with JSON only, no prose: {"intent":"save"|"search"|"unclear"}
			""".trim();

	private final ChatModelPort chat;
	private final ObjectMapper objectMapper;

	public ChatActionIntentClassifier(ChatModelPort chat) {
		this(chat, new ObjectMapper());
	}

	ChatActionIntentClassifier(ChatModelPort chat, ObjectMapper objectMapper) {
		this.chat = chat;
		this.objectMapper = objectMapper;
	}

	@Override
	public ActionIntent classify(RoutedRequest request) {
		String user = buildUserMessage(request);
		String raw;
		try {
			raw = chat.complete(SYSTEM_PROMPT, user);
		}
		catch (RuntimeException e) {
			log.warn("Chat model failed during action intent classify: {}", e.toString());
			throw new ActionIntentClassificationException("Action Intent classification failed", e);
		}
		return parseIntent(raw);
	}

	private static String buildUserMessage(RoutedRequest request) {
		StringBuilder sb = new StringBuilder();
		sb.append("locus: ").append(request.locus().name().toLowerCase(Locale.ROOT)).append('\n');
		sb.append("forwarded: ").append(request.forwarded()).append('\n');
		sb.append("urls: ");
		if (request.urls().isEmpty()) {
			sb.append("(none)");
		}
		else {
			sb.append(request.urls().stream().map(u -> u.url()).toList());
		}
		sb.append('\n');
		sb.append("text:\n").append(request.text() == null ? "" : request.text());
		return sb.toString();
	}

	private ActionIntent parseIntent(String raw) {
		Optional<JsonNode> json = extractJson(raw);
		if (json.isEmpty()) {
			throw new ActionIntentClassificationException("Action Intent response was not valid JSON");
		}
		JsonNode node = json.get();
		JsonNode intentNode = node.get("intent");
		if (intentNode == null || intentNode.isNull() || !intentNode.isTextual()) {
			throw new ActionIntentClassificationException("Action Intent JSON missing intent field");
		}
		String value = intentNode.asText().trim().toLowerCase(Locale.ROOT);
		return switch (value) {
			case "save" -> ActionIntent.SAVE;
			case "search" -> ActionIntent.SEARCH;
			case "unclear" -> ActionIntent.UNCLEAR;
			default -> throw new ActionIntentClassificationException("Unknown Action Intent: " + value);
		};
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
					log.debug("Could not parse action intent JSON: {}", e.toString());
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
}
