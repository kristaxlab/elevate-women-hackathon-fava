package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Telegram {@code MessageOrigin} (forward_origin). Only presence matters for Inbox accept rules.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramForwardOrigin(String type) {
}
