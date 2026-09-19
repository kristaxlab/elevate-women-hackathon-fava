package com.fava.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramChatMemberUpdated(
		TelegramChat chat,
		TelegramUser from,
		@JsonProperty("old_chat_member") TelegramChatMember oldChatMember,
		@JsonProperty("new_chat_member") TelegramChatMember newChatMember) {
}
