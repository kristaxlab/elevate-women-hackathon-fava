package com.fava.catalog;

/** Invalid create request (e.g. client-supplied id). */
public final class InvalidSavedItemCreateException extends RuntimeException {

	public InvalidSavedItemCreateException(String message) {
		super(message);
	}
}
