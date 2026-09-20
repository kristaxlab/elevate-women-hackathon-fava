package com.fava.catalog;

/** Embedding provider missing or failed while creating a Saved Item. */
public final class EmbeddingProviderException extends RuntimeException {

	public EmbeddingProviderException(String message) {
		super(message);
	}

	public EmbeddingProviderException(String message, Throwable cause) {
		super(message, cause);
	}
}
