package com.fava.intent;

/**
 * Thrown when Action Intent classification cannot complete (model error, etc.).
 */
public final class ActionIntentClassificationException extends RuntimeException {

	public ActionIntentClassificationException(String message, Throwable cause) {
		super(message, cause);
	}

	public ActionIntentClassificationException(String message) {
		super(message);
	}
}
