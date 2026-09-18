package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Minimal Bot API update shape used by DM routing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
		@JsonProperty("update_id") Long updateId,
		TelegramMessage message) {
}
