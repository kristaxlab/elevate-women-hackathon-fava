package com.fava.catalog;

/** Theme Topic name is not in the Catalog. */
public final class ThemeNotFoundException extends RuntimeException {

	private final long chatId;
	private final String themeName;

	public ThemeNotFoundException(long chatId, String themeName) {
		super("theme not found in catalog " + chatId + ": " + themeName);
		this.chatId = chatId;
		this.themeName = themeName;
	}

	public long chatId() {
		return chatId;
	}

	public String themeName() {
		return themeName;
	}
}
