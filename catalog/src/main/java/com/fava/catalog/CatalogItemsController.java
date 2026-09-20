package com.fava.catalog;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Developer HTTP adapter for Catalog Saved Item writes and list.
 */
@RestController
public class CatalogItemsController {

	private final CatalogItemCreator creator;
	private final CatalogItemLister lister;

	public CatalogItemsController(CatalogItemCreator creator, CatalogItemLister lister) {
		this.creator = creator;
		this.lister = lister;
	}

	@PostMapping("/api/catalog/items")
	public ResponseEntity<SavedItemResponse> create(@RequestBody CreateSavedItemRequest body) {
		SavedItemCreateResult result = creator.create(body.toSavedItem());
		return switch (result) {
			case SavedItemCreateResult.Created created -> ResponseEntity.status(HttpStatus.CREATED)
					.body(new SavedItemResponse(created.item(), EmbeddingResponse.from(created.embedding())));
			case SavedItemCreateResult.Duplicate duplicate -> ResponseEntity.ok(new SavedItemResponse(
					duplicate.item(),
					duplicate.embedding().map(EmbeddingResponse::from).orElse(null)));
		};
	}

	@GetMapping("/api/catalog/items")
	public List<SavedItemResponse> list(@RequestParam("chatId") long chatId) {
		return lister.list(chatId).stream()
				.map(listed -> new SavedItemResponse(
						listed.item(),
						listed.embedding().map(EmbeddingResponse::from).orElse(null)))
				.toList();
	}
}
