package com.fava.search;

import com.fava.catalog.CatalogNotFoundException;
import com.fava.catalog.EmbeddingProviderException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 7807 problem+json mapping for Search developer HTTP endpoints.
 */
@RestControllerAdvice(assignableTypes = SearchItemsController.class)
public class SearchApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(SearchApiExceptionHandler.class);

	static final URI TYPE_VALIDATION = URI.create("https://fava.local/problems/validation");
	static final URI TYPE_CATALOG_NOT_FOUND = URI.create("https://fava.local/problems/catalog-not-found");
	static final URI TYPE_NO_SEARCH_HITS = URI.create("https://fava.local/problems/no-search-hits");
	static final URI TYPE_PROVIDER = URI.create("https://fava.local/problems/provider");
	static final URI TYPE_INTERNAL = URI.create("https://fava.local/problems/internal");

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail illegalArgument(IllegalArgumentException ex) {
		return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation Failed", ex.getMessage());
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ProblemDetail missingParam(MissingServletRequestParameterException ex) {
		return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation Failed", ex.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail notReadable(HttpMessageNotReadableException ex) {
		log.warn("Unreadable search request: {}", ex.getMessage());
		Throwable cause = ex.getMostSpecificCause();
		if (cause instanceof IllegalArgumentException iae) {
			return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation Failed", iae.getMessage());
		}
		return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation Failed", "Malformed JSON request body");
	}

	@ExceptionHandler(CatalogNotFoundException.class)
	ProblemDetail catalogNotFound(CatalogNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, TYPE_CATALOG_NOT_FOUND, "Catalog Not Found", ex.getMessage());
	}

	@ExceptionHandler(NoSearchHitsException.class)
	ProblemDetail noSearchHits(NoSearchHitsException ex) {
		return problem(HttpStatus.NOT_FOUND, TYPE_NO_SEARCH_HITS, "No Search Hits", ex.getMessage());
	}

	@ExceptionHandler(EmbeddingProviderException.class)
	ProblemDetail provider(EmbeddingProviderException ex) {
		log.warn("Search provider error: {}", ex.toString());
		return problem(HttpStatus.BAD_GATEWAY, TYPE_PROVIDER, "Provider Error", ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception ex) {
		log.error("Unexpected search API error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, TYPE_INTERNAL, "Internal Error", "Unexpected error");
	}

	private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setType(type);
		pd.setTitle(title);
		return pd;
	}
}
