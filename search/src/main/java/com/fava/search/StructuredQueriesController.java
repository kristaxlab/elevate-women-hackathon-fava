package com.fava.search;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Developer HTTP adapter for natural-language to structured query parsing.
 */
@RestController
public class StructuredQueriesController {

	private final StructuredQueryParser parser;

	public StructuredQueriesController(StructuredQueryParser parser) {
		this.parser = parser;
	}

	@PostMapping("/api/search/structured-queries")
	public StructuredQuery parse(@RequestBody StructuredQueryRequest body) {
		return parser.parseStrict(body.query());
	}
}
