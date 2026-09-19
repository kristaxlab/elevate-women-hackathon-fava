package com.fava.telegram;

/**
 * Whether a user is a Catalog Owner–eligible admin in a Telegram chat (creator or administrator).
 */
public interface ChatAdminPort {

	boolean isAdmin(long chatId, long userId);
}
