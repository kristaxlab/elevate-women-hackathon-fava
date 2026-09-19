package com.fava.intent;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Offline / no-API-key Action Intent.
 * Prefer search when the text looks like a question (including questions that include a URL);
 * otherwise URL/forward → save; Inbox noise without those → unclear; other non-blank → search.
 */
public final class HeuristicActionIntentClassifier implements ActionIntentClassifier {

	private static final Pattern HTTP_URL = Pattern.compile("https?://", Pattern.CASE_INSENSITIVE);

	@Override
	public ActionIntent classify(RoutedRequest request) {
		String text = request.text();
		if (looksLikeQuestion(text)) {
			return ActionIntent.SEARCH;
		}
		if (request.forwarded() || hasUrl(request)) {
			return ActionIntent.SAVE;
		}
		if (text == null || text.isBlank()) {
			return ActionIntent.UNCLEAR;
		}
		if (request.locus() == MessageLocus.INBOX) {
			return ActionIntent.UNCLEAR;
		}
		return ActionIntent.SEARCH;
	}

	private static boolean looksLikeQuestion(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		String trimmed = text.trim();
		if (trimmed.indexOf('?') >= 0) {
			return true;
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		return lower.startsWith("what ")
				|| lower.startsWith("where ")
				|| lower.startsWith("when ")
				|| lower.startsWith("why ")
				|| lower.startsWith("how ")
				|| lower.startsWith("who ")
				|| lower.startsWith("which ")
				|| lower.startsWith("do i ")
				|| lower.startsWith("did i ")
				|| lower.startsWith("any ")
				|| lower.startsWith("is there ");
	}

	private static boolean hasUrl(RoutedRequest request) {
		if (!request.urls().isEmpty()) {
			return true;
		}
		String text = request.text();
		return text != null && HTTP_URL.matcher(text).find();
	}
}
