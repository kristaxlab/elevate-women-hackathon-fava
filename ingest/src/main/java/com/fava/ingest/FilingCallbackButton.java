package com.fava.ingest;

/**
 * Inline callback button for Theme Topic picks (Telegram {@code callback_data}).
 */
public record FilingCallbackButton(String label, String callbackData) {
}
