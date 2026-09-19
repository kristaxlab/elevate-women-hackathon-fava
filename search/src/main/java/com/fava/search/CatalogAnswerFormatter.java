package com.fava.search;

/**
 * Formats a {@link CatalogSearchResult} for a Smart Search Telegram reply.
 */
public final class CatalogAnswerFormatter {

	private CatalogAnswerFormatter() {
	}

	public static String format(CatalogSearchResult result) {
		return switch (result) {
			case CatalogSearchResult.NothingFound nothing -> nothing.message();
			case CatalogSearchResult.Answer answer -> formatAnswer(answer);
		};
	}

	private static String formatAnswer(CatalogSearchResult.Answer answer) {
		StringBuilder sb = new StringBuilder();
		sb.append(answer.text().trim());
		if (!answer.citations().isEmpty()) {
			sb.append("\n\nCitations:");
			for (CatalogSearchResult.Citation citation : answer.citations()) {
				sb.append("\n• ").append(citation.snippet());
				citation.url().ifPresent(url -> sb.append("\n  ").append(url));
			}
		}
		return sb.toString();
	}
}
