package com.fava.search;

import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Developer HTTP adapter for structured Catalog item search.
 */
@RestController
public class SearchItemsController {

	private final StructuredItemsSearcher searcher;

	public SearchItemsController(StructuredItemsSearcher searcher) {
		this.searcher = searcher;
	}

	@PostMapping("/api/search/items")
	public List<SearchHitResponse> search(
			@RequestParam("chatId") long chatId,
			@RequestParam(value = "maxDistance", defaultValue = "0.45") double maxDistance,
			@RequestBody StructuredQuery body) {
		return searcher.search(chatId, body, maxDistance).stream()
				.map(hit -> new SearchHitResponse(hit.item(), hit.distance()))
				.toList();
	}
}
