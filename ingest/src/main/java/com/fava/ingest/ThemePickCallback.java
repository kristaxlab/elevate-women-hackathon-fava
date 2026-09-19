package com.fava.ingest;

import java.util.Optional;

/**
 * Encodes Theme Topic pick callbacks within Telegram's 64-byte {@code callback_data} limit.
 * Format: {@code f6:<sourceMessageId>:<themeIndex>} (Theme Topics only; never Inbox/Smart Search).
 */
public final class ThemePickCallback {

	static final String PREFIX = "f6:";

	private ThemePickCallback() {
	}

	public static String encode(long sourceMessageId, int themeIndex) {
		String data = PREFIX + sourceMessageId + ":" + themeIndex;
		if (data.length() > 64) {
			throw new IllegalArgumentException("callback_data exceeds 64 bytes: " + data.length());
		}
		return data;
	}

	public static boolean isThemePick(String callbackData) {
		return callbackData != null && callbackData.startsWith(PREFIX);
	}

	public static Optional<Parsed> parse(String callbackData) {
		if (!isThemePick(callbackData)) {
			return Optional.empty();
		}
		String rest = callbackData.substring(PREFIX.length());
		int colon = rest.lastIndexOf(':');
		if (colon <= 0 || colon == rest.length() - 1) {
			return Optional.empty();
		}
		try {
			long sourceMessageId = Long.parseLong(rest.substring(0, colon));
			int themeIndex = Integer.parseInt(rest.substring(colon + 1));
			if (themeIndex < 0) {
				return Optional.empty();
			}
			return Optional.of(new Parsed(sourceMessageId, themeIndex));
		}
		catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	public record Parsed(long sourceMessageId, int themeIndex) {
	}
}
