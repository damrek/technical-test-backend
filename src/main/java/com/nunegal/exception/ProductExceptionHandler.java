package com.nunegal.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates the one failure the API is allowed to report as {@code 404}.
 */
@RestControllerAdvice
public class ProductExceptionHandler {

	/**
	 * @param ex raised because the product API does not know the requested product
	 * @return an empty 404
	 */
	@ExceptionHandler(ProductNotFoundException.class)
	public ResponseEntity<Void> handleProductNotFound(ProductNotFoundException ex) {
		return ResponseEntity.notFound().build();
	}

}