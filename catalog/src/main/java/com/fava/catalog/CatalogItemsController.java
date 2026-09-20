package com.fava.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Developer HTTP adapter for Catalog Saved Item writes.
 */
@RestController
public class CatalogItemsController {

	private final CatalogItemCreator creator;

	public CatalogItemsController(CatalogItemCreator creator) {
		this.creator = creator;
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
}
