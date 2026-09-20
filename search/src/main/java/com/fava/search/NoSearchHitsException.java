package com.fava.search;

/** Structured search returned no hits (filter miss or all above distance cutoff). */
public final class NoSearchHitsException extends RuntimeException {

	private final long chatId;

	public NoSearchHitsException(long chatId) {
		super("no search hits for catalog: " + chatId);
		this.chatId = chatId;
	}

	public long chatId() {
		return chatId;
	}
}
