package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Bot API update shape for DM and group Catalog Setup routing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
		@JsonProperty("update_id") Long updateId,
		TelegramMessage message,
		@JsonProperty("my_chat_member") TelegramChatMemberUpdated myChatMember) {

	public TelegramUpdate(Long updateId, TelegramMessage message) {
		this(updateId, message, null);
	}
}
