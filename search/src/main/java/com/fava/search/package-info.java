/**
 * Search: Catalog Question RAG and Catalog Answers grounded in Saved Items.
 *
 * <p>Embeddings are written on Filing (index-on-save). Items filed before this feature
 * (or while OpenRouter was unconfigured) are not backfilled and will not appear in
 * vector search until re-filed; keyword degraded mode can still match their body text.
 */
package com.fava.search;
