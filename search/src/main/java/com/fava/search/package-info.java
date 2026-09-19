/**
 * Search: Catalog Question RAG and Catalog Answers grounded in Saved Items.
 *
 * <p>Embeddings are written on Filing (index-on-save) and re-synced on startup when the
 * configured embedding model or dimensions change (see {@link CatalogEmbeddingSync}).
 * Keyword degraded mode (no API key) can still match body text without vectors.
 */
package com.fava.search;
