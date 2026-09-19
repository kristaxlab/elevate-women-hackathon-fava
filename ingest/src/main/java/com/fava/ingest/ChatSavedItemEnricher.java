package com.fava.ingest;

import com.fava.catalog.SourceType;
import com.fava.classify.ChatModelPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * LLM enrichment of Saved Item metadata plus URL heuristics when the model fails or omits type.
 */
public final class ChatSavedItemEnricher implements SavedItemEnricher {

	private static final Logger log = LoggerFactory.getLogger(ChatSavedItemEnricher.class);

	private static final String SYSTEM = """
			You enrich a personal catalog save. Reply with JSON only (no markdown):
			{"source_type":"instagram|youtube|linkedin|book|movie|game|recipe|article|note|other|unknown",\
			"title":"short English title","recommended_by":"person or null",\
			"tags":["lowercase","tags"],"search_text":"English paragraph for semantic search"}
			search_text must be English even if the body is another language. Omit unknowns as null or [].
			""";

	private final ChatModelPort chat;
	private final ObjectMapper objectMapper;

	public ChatSavedItemEnricher(ChatModelPort chat) {
		this(chat, new ObjectMapper());
	}

	ChatSavedItemEnricher(ChatModelPort chat, ObjectMapper objectMapper) {
		this.chat = chat;
		this.objectMapper = objectMapper;
	}

	@Override
	public SavedItemEnrichment enrich(Optional<String> url, String bodyText) {
		Optional<SourceType> heuristicType = UrlSourceTypeHeuristics.fromUrl(url);
		String user = buildUserMessage(url, bodyText);
		String raw;
		try {
			raw = chat.complete(SYSTEM, user);
		}
		catch (RuntimeException e) {
			log.warn("Chat model failed during Saved Item enrichment: {}", e.toString());
			return heuristicOnly(heuristicType);
		}
		return parseOrHeuristic(raw, heuristicType);
	}

	private SavedItemEnrichment parseOrHeuristic(String raw, Optional<SourceType> heuristicType) {
		Optional<JsonNode> json = extractJson(raw);
		if (json.isEmpty()) {
			return heuristicOnly(heuristicType);
		}
		JsonNode node = json.get();
		Optional<SourceType> parsedType = parseSourceType(textOrNull(node, "source_type"));
		Optional<SourceType> sourceType = parsedType
				.filter(t -> t != SourceType.UNKNOWN)
				.or(() -> heuristicType)
				.or(() -> parsedType);
		Optional<String> title = optionalNonBlank(textOrNull(node, "title"));
		Optional<String> recommendedBy = optionalNonBlank(textOrNull(node, "recommended_by"));
		List<String> tags = parseTags(node.get("tags"));
		Optional<String> searchText = optionalNonBlank(textOrNull(node, "search_text"));
		return new SavedItemEnrichment(sourceType, title, recommendedBy, tags, searchText);
	}

	private static SavedItemEnrichment heuristicOnly(Optional<SourceType> heuristicType) {
		return new SavedItemEnrichment(
				heuristicType, Optional.empty(), Optional.empty(), List.of(), Optional.empty());
	}

	private static String buildUserMessage(Optional<String> url, String bodyText) {
		String body = bodyText == null ? "" : bodyText;
		if (url != null && url.isPresent()) {
			return body + "\nURL: " + url.get();
		}
		return body;
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
				catch (Exception ignoredAgain) {
					return Optional.empty();
				}
			}
			return Optional.empty();
		}
	}

	private static String stripMarkdownFence(String raw) {
		if (!raw.startsWith("```")) {
			return raw;
		}
		int firstNl = raw.indexOf('\n');
		if (firstNl < 0) {
			return raw;
		}
		String withoutOpen = raw.substring(firstNl + 1);
		int fence = withoutOpen.lastIndexOf("```");
		if (fence >= 0) {
			return withoutOpen.substring(0, fence).trim();
		}
		return withoutOpen.trim();
	}

	private static Optional<SourceType> parseSourceType(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(SourceType.fromWire(raw.trim().toLowerCase(Locale.ROOT)));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static List<String> parseTags(JsonNode tagsNode) {
		if (tagsNode == null || !tagsNode.isArray()) {
			return List.of();
		}
		List<String> tags = new ArrayList<>();
		for (JsonNode t : tagsNode) {
			if (t != null && t.isTextual()) {
				String s = t.asText();
				if (s != null && !s.isBlank()) {
					tags.add(s.trim().toLowerCase(Locale.ROOT));
				}
			}
		}
		return List.copyOf(tags);
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode child = node.get(field);
		if (child == null || child.isNull() || !child.isTextual()) {
			return null;
		}
		String s = child.asText();
		return s == null || s.isBlank() || "null".equalsIgnoreCase(s) ? null : s;
	}

	private static Optional<String> optionalNonBlank(String value) {
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
	}
}
