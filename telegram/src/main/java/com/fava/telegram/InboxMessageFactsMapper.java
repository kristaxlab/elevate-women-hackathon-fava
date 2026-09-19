package com.fava.telegram;

import com.fava.ingest.ExtractedUrl;
import com.fava.ingest.InboxMessageFacts;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps Telegram message fields into ingest facts (no network).
 */
final class InboxMessageFactsMapper {

	private InboxMessageFactsMapper() {
	}

	static InboxMessageFacts from(TelegramMessage message) {
		String body = message.bodyText();
		List<ExtractedUrl> urls = new ArrayList<>();
		urls.addAll(fromEntities(body, message.entities()));
		urls.addAll(fromEntities(message.caption(), message.captionEntities()));
		return new InboxMessageFacts(
				message.chat().id(),
				message.messageId(),
				message.messageThreadId(),
				body,
				urls,
				message.isForwarded(),
				null);
	}

	private static List<ExtractedUrl> fromEntities(String text, List<TelegramMessageEntity> entities) {
		if (entities == null || entities.isEmpty()) {
			return List.of();
		}
		List<ExtractedUrl> urls = new ArrayList<>();
		for (TelegramMessageEntity entity : entities) {
			if (entity == null || entity.type() == null) {
				continue;
			}
			if ("text_link".equals(entity.type()) && entity.url() != null && !entity.url().isBlank()) {
				urls.add(new ExtractedUrl(entity.url(), entity.offset(), entity.length()));
			}
			else if ("url".equals(entity.type()) && text != null) {
				int start = Math.max(0, entity.offset());
				int end = Math.min(text.length(), start + entity.length());
				if (start < end) {
					urls.add(new ExtractedUrl(text.substring(start, end), start, end - start));
				}
			}
		}
		return urls;
	}
}
