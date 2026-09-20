package com.fava.catalog;

/** Catalog {@code chatId} has no setup row. */
public final class CatalogNotFoundException extends RuntimeException {

	private final long chatId;

	public CatalogNotFoundException(long chatId) {
		super("catalog not found: " + chatId);
		this.chatId = chatId;
	}

	public long chatId() {
		return chatId;
	}
}
