package com.fava.ingest;

import com.fava.catalog.SourceType;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Rough {@link SourceType} from a content URL host when the LLM is unavailable or inconclusive.
 */
public final class UrlSourceTypeHeuristics {

	private UrlSourceTypeHeuristics() {
	}

	public static Optional<SourceType> fromUrl(Optional<String> url) {
		if (url == null || url.isEmpty()) {
			return Optional.empty();
		}
		String raw = url.get().trim();
		if (raw.isEmpty()) {
			return Optional.empty();
		}
		try {
			URI uri = URI.create(raw);
			String host = uri.getHost();
			if (host == null || host.isBlank()) {
				return Optional.empty();
			}
			String h = host.toLowerCase(Locale.ROOT);
			if (h.equals("instagram.com") || h.endsWith(".instagram.com")) {
				return Optional.of(SourceType.INSTAGRAM);
			}
			if (h.equals("youtube.com") || h.endsWith(".youtube.com") || h.equals("youtu.be")) {
				return Optional.of(SourceType.YOUTUBE);
			}
			if (h.equals("linkedin.com") || h.endsWith(".linkedin.com")) {
				return Optional.of(SourceType.LINKEDIN);
			}
			return Optional.empty();
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			return Optional.empty();
		}
	}
}
