package com.fava.catalog;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 7807 problem+json mapping for Catalog developer HTTP endpoints.
 */
@RestControllerAdvice(assignableTypes = CatalogItemsController.class)
public class CatalogApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(CatalogApiExceptionHandler.class);

	static final URI TYPE_VALIDATION = URI.create("https://fava.local/problems/validation");
	static final URI TYPE_CATALOG_NOT_FOUND = URI.create("https://fava.local/problems/catalog-not-found");
	static final URI TYPE_THEME_NOT_FOUND = URI.create("https://fava.local/problems/theme-not-found");
	static final URI TYPE_PROVIDER = URI.create("https://fava.local/problems/provider");
	static final URI TYPE_INTERNAL = URI.create("https://fava.local/problems/internal");

	@ExceptionHandler(InvalidSavedItemCreateException.class)
	ProblemDetail invalidCreate(InvalidSavedItemCreateException ex) {
		return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation Failed", ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail illegalArgument(IllegalArgumentException ex) {
		return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation Failed", ex.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail notReadable(HttpMessageNotReadableException ex) {
		log.warn("Unreadable catalog request: {}", ex.getMessage());
		return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation Failed", "Malformed JSON request body");
	}

	@ExceptionHandler(CatalogNotFoundException.class)
	ProblemDetail catalogNotFound(CatalogNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, TYPE_CATALOG_NOT_FOUND, "Catalog Not Found", ex.getMessage());
	}

	@ExceptionHandler(ThemeNotFoundException.class)
	ProblemDetail themeNotFound(ThemeNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, TYPE_THEME_NOT_FOUND, "Theme Topic Not Found", ex.getMessage());
	}

	@ExceptionHandler(EmbeddingProviderException.class)
	ProblemDetail provider(EmbeddingProviderException ex) {
		log.warn("Embedding provider error: {}", ex.toString());
		return problem(HttpStatus.BAD_GATEWAY, TYPE_PROVIDER, "Provider Error", ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception ex) {
		log.error("Unexpected catalog API error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, TYPE_INTERNAL, "Internal Error", "Unexpected error");
	}

	private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setType(type);
		pd.setTitle(title);
		return pd;
	}
}
