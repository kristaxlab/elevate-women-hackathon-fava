package com.fava.telegram;

/**
 * An inline keyboard button that sends {@code callback_data} (not a URL).
 */
public record InlineCallbackButton(String text, String callbackData) {
}
