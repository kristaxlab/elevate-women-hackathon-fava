package com.fava.ingest;

/**
 * A URL already extracted from message text or entities (no network fetch).
 */
public record ExtractedUrl(String url, int offset, int length) {
}
