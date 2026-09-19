package com.fava.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes Inbox message facts into an Accepted draft or Rejected reason.
 * Accepts http(s) URLs and/or forwarded posts with text. No network calls.
 */
public final class InboxMessageNormalizer {

	static final String REJECT_UNSUPPORTED =
			"I only accept http(s) links or forwarded Telegram posts in Inbox.";
	static final String REJECT_EMPTY = "Nothing to save — send a link or forward a post.";

	private static final Pattern HTTP_URL = Pattern.compile(
			"https?://[^\\s<>\\[\\]()\"']+",
			Pattern.CASE_INSENSITIVE);

	public NormalizeResult normalize(InboxMessageFacts facts) {
		if (facts == null) {
			return new NormalizeResult.Rejected(REJECT_EMPTY);
		}
		String body = bodyText(facts);
		Optional<String> url = firstUrl(facts, body);

		boolean hasText = body != null && !body.isBlank();
		if (!hasText && url.isEmpty()) {
			return new NormalizeResult.Rejected(REJECT_EMPTY);
		}
		if (url.isEmpty() && !facts.forwarded()) {
			return new NormalizeResult.Rejected(REJECT_UNSUPPORTED);
		}
		if (!hasText) {
			return new NormalizeResult.Rejected(REJECT_EMPTY);
		}
		return new NormalizeResult.Accepted(new AcceptedDraft(
				facts.chatId(),
				facts.messageId(),
				facts.threadId(),
				url,
				body.trim()));
	}

	private static String bodyText(InboxMessageFacts facts) {
		if (facts.text() != null && !facts.text().isBlank()) {
			return facts.text();
		}
		if (facts.forwardOriginText() != null && !facts.forwardOriginText().isBlank()) {
			return facts.forwardOriginText();
		}
		return facts.text();
	}

	private static Optional<String> firstUrl(InboxMessageFacts facts, String body) {
		List<String> candidates = new ArrayList<>();
		for (ExtractedUrl extracted : facts.urls()) {
			if (extracted != null && isHttpUrl(extracted.url())) {
				candidates.add(extracted.url());
			}
		}
		if (body != null) {
			Matcher matcher = HTTP_URL.matcher(body);
			while (matcher.find()) {
				candidates.add(trimTrailingPunctuation(matcher.group()));
			}
		}
		return candidates.stream().findFirst();
	}

	private static boolean isHttpUrl(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		String lower = value.toLowerCase();
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	private static String trimTrailingPunctuation(String url) {
		int end = url.length();
		while (end > 0) {
			char c = url.charAt(end - 1);
			if (c == '.' || c == ',' || c == ';' || c == ':' || c == ')' || c == ']' || c == '}') {
				end--;
			}
			else {
				break;
			}
		}
		return url.substring(0, end);
	}
}
