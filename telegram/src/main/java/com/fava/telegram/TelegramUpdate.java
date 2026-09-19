package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Bot API update shape for DM, group Catalog Setup, Inbox ingest, and Theme Topic picks.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
		@JsonProperty("update_id") Long updateId,
		TelegramMessage message,
		@JsonProperty("my_chat_member") TelegramChatMemberUpdated myChatMember,
		@JsonProperty("callback_query") TelegramCallbackQuery callbackQuery) {

	public TelegramUpdate(Long updateId, TelegramMessage message) {
		this(updateId, message, null, null);
	}

	public TelegramUpdate(Long updateId, TelegramMessage message, TelegramChatMemberUpdated myChatMember) {
		this(updateId, message, myChatMember, null);
	}
}
