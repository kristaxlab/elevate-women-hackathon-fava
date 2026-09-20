package com.fava.search;

import com.fava.catalog.EmbeddingProviderException;
import com.fava.classify.ChatModelPort;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses a Catalog Question into a {@link StructuredQuery} via chat completion.
 */
public final class StructuredQueryParser {

	private static final Logger log = LoggerFactory.getLogger(StructuredQueryParser.class);

	static final String SYSTEM_PROMPT = """
			You turn a Catalog Question into a structured Saved Item search.
			Reply with JSON only (no markdown):
			{"query":"english retrieval string","limit":1-10,"filters":{"source_type":"...",\
			"since":"ISO-8601","recommended_by":"...","tags":["..."],"theme_name":"..."}}
			query is an English string for semantic retrieval (translate if needed).
			limit defaults to 3; honor a user-stated N up to 10.
			Omit any filter not clearly stated in the question (empty filters object is fine).
			tags are AND when present. source_type when used must be one of:
			instagram, youtube, linkedin, book, movie, game, recipe, article, note, other, unknown.
			""";

	private final ChatModelPort chat;
	private final ObjectMapper objectMapper;

	public StructuredQueryParser(ChatModelPort chat) {
		this(chat, new ObjectMapper());
	}

	StructuredQueryParser(ChatModelPort chat, ObjectMapper objectMapper) {
		this.chat = chat;
		this.objectMapper = objectMapper;
	}

	public StructuredQuery parse(String catalogQuestion) {
		if (catalogQuestion == null || catalogQuestion.isBlank()) {
			throw new IllegalArgumentException("catalogQuestion must not be blank");
		}
		String fallbackQuery = catalogQuestion.trim();
		String raw;
		try {
			raw = chat.complete(SYSTEM_PROMPT, fallbackQuery);
		}
		catch (RuntimeException e) {
			log.warn("Chat model failed during structured query parse: {}", e.toString());
			return fallback(fallbackQuery);
		}
		return parseOrFallback(raw, fallbackQuery);
	}

	public StructuredQuery parseStrict(String catalogQuestion) {
		if (catalogQuestion == null || catalogQuestion.isBlank()) {
			throw new IllegalArgumentException("catalogQuestion must not be blank");
		}
		String trimmedQuestion = catalogQuestion.trim();
		String raw;
		try {
			raw = chat.complete(SYSTEM_PROMPT, trimmedQuestion);
		}
		catch (IllegalStateException e) {
			log.warn("Chat model failed during structured query parse: {}", e.toString());
			throw new EmbeddingProviderException("Chat model failed", e);
		}
		return parseOrThrow(raw);
	}

	private StructuredQuery parseOrFallback(String raw, String fallbackQuery) {
		Optional<JsonNode> json = extractJson(raw);
		if (json.isEmpty()) {
			return fallback(fallbackQuery);
		}
		JsonNode node = json.get();
		String query = textOrNull(node, "query");
		if (query == null) {
			return fallback(fallbackQuery);
		}
		int limit = intOrDefault(node, "limit", StructuredQuery.DEFAULT_LIMIT);
		StructuredQuery.Filters filters = parseFilters(node.get("filters"));
		return new StructuredQuery(query, limit, filters);
	}

	private StructuredQuery parseOrThrow(String raw) {
		Optional<JsonNode> json = extractJson(raw);
		if (json.isEmpty()) {
			throw new EmbeddingProviderException("Chat model returned invalid structured query JSON");
		}
		JsonNode node = json.get();
		String query = textOrNull(node, "query");
		if (query == null) {
			throw new EmbeddingProviderException("Chat model returned invalid structured query JSON");
		}
		int limit = intOrDefault(node, "limit", StructuredQuery.DEFAULT_LIMIT);
		StructuredQuery.Filters filters = parseFilters(node.get("filters"));
		return new StructuredQuery(query, limit, filters);
	}

	private static StructuredQuery fallback(String query) {
		return new StructuredQuery(query, StructuredQuery.DEFAULT_LIMIT, StructuredQuery.Filters.NONE);
	}

	private StructuredQuery.Filters parseFilters(JsonNode filtersNode) {
		if (filtersNode == null || filtersNode.isNull() || !filtersNode.isObject()) {
			return StructuredQuery.Filters.NONE;
		}
		return new StructuredQuery.Filters(
				optionalNonBlank(textOrNull(filtersNode, "source_type")),
				parseSince(textOrNull(filtersNode, "since")),
				optionalNonBlank(textOrNull(filtersNode, "recommended_by")),
				parseTags(filtersNode.get("tags")),
				optionalNonBlank(textOrNull(filtersNode, "theme_name")));
	}

	private static Optional<Instant> parseSince(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(Instant.parse(raw.trim()));
		}
		catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}

	private static List<String> parseTags(JsonNode tagsNode) {
		if (tagsNode == null || tagsNode.isNull() || !tagsNode.isArray()) {
			return List.of();
		}
		List<String> tags = new ArrayList<>();
		for (JsonNode tag : tagsNode) {
			if (tag != null && tag.isTextual()) {
				String text = tag.asText();
				if (text != null && !text.isBlank()) {
					tags.add(text.trim());
				}
			}
		}
		return tags;
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
					log.debug("Could not parse structured query JSON: {}", e.toString());
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

	private static int intOrDefault(JsonNode node, String field, int defaultValue) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull() || !value.isNumber()) {
			return defaultValue;
		}
		return value.asInt();
	}

	private static Optional<String> optionalNonBlank(String value) {
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
	}
}
