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
			case CatalogSearchResult.Unavailable unavailable -> unavailable.message();
			case CatalogSearchResult.Answer answer -> formatAnswer(answer);
		};
	}

	private static String formatAnswer(CatalogSearchResult.Answer answer) {
		StringBuilder sb = new StringBuilder();
		String intro = answer.intro().trim();
		if (!intro.isEmpty()) {
			sb.append(intro);
		}
		if (!answer.items().isEmpty()) {
			if (!sb.isEmpty()) {
				sb.append("\n\n");
			}
			int n = 1;
			for (CatalogSearchResult.Citation item : answer.items()) {
				if (n > 1) {
					sb.append('\n');
				}
				sb.append(n).append(". ").append(item.title());
				item.sourceType().ifPresent(type -> sb.append(" (").append(type).append(')'));
				item.themeTopicLink().ifPresentOrElse(
						link -> sb.append('\n').append("   ").append(link),
						() -> item.url().ifPresent(url -> sb.append('\n').append("   ").append(url)));
				n++;
			}
		}
		return sb.toString();
	}
}
