package com.fava.intent;

/**
 * Outbound seam for Intent Router participant-facing replies (redirect, clarify, nudge, failures,
 * and search answers). Filing confirmations stay on the ingest filing port.
 */
public interface ParticipantNotifyPort {

	void replyToMessage(long chatId, long messageId, String text);
}
