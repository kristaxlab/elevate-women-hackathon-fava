/**
 * Search: Catalog Question structured list retrieval and Catalog Answers grounded in Saved Items.
 *
 * <p>Embeddings are written on Filing (index-on-save) and re-synced on startup when the
 * configured embedding model or dimensions change (see {@link CatalogEmbeddingSync}).
 * Without an OpenRouter API key, Smart Search replies that AI is unavailable (no keyword fallback).
 */
package com.fava.search;
