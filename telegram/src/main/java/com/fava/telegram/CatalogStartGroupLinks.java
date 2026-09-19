package com.fava.telegram;

/**
 * Builds Telegram {@code startgroup} deep links that open the add-to-group flow with
 * preselected admin rights. Does not create groups via the Bot API.
 *
 * <p>Admin flags (Telegram {@code admin=} query, joined with {@code +}):
 * {@code change_info}, {@code delete_messages}, {@code restrict_members}, {@code pin_messages},
 * and {@code manage_topics} — the set needed for forum Catalog setup (especially topic
 * management) plus typical message-admin rights for an admin bot.
 *
 * @see <a href="https://core.telegram.org/api/links">Telegram deep links</a>
 */
final class CatalogStartGroupLinks {

	/**
	 * Rights requested when adding Fava as a group admin for forum Catalog setup.
	 */
	static final String ADMIN_PERMISSIONS =
			"change_info+delete_messages+restrict_members+pin_messages+manage_topics";

	private CatalogStartGroupLinks() {
	}

	static String createCatalog(String botUsername) {
		return startGroupUrl(botUsername, "create");
	}

	static String existingGroup(String botUsername) {
		return startGroupUrl(botUsername, "existing");
	}

	private static String startGroupUrl(String botUsername, String startgroupParam) {
		String username = stripAt(botUsername);
		return "https://t.me/" + username + "?startgroup=" + startgroupParam + "&admin=" + ADMIN_PERMISSIONS;
	}

	private static String stripAt(String botUsername) {
		if (botUsername != null && botUsername.startsWith("@")) {
			return botUsername.substring(1);
		}
		return botUsername;
	}
}
