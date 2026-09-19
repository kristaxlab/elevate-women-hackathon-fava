package com.fava.ingest;

import com.fava.catalog.SavedItem;

/**
 * Outcome of Filing an Accepted draft into a Catalog.
 */
public sealed interface FilingResult {

	record Filed(String themeName, SavedItem item) implements FilingResult {
	}

	record AlreadyFiled(String themeName) implements FilingResult {
	}
}
