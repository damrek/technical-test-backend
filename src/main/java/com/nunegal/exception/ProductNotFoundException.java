package com.nunegal.exception;

/**
 * Signals that the product API reported the requested product does not exist.
 *
 * <p>
 * A dedicated type because the two places this is raised mean opposite things to the endpoint.
 * Raised for the similar-id lookup it is the only condition that may become a 404. Raised for a
 * single product detail it is a permanently broken child that must simply be left out of the
 * result. Collapsing both into a generic error would force the aggregation and web layers to
 * inspect HTTP status codes to tell those cases apart.
 */
public class ProductNotFoundException extends RuntimeException {

	private final String productId;

	public ProductNotFoundException(String productId) {
		super("Product not found: " + productId);
		this.productId = productId;
	}

	/**
	 * @return the identifier that was not found
	 */
	public String getProductId() {
		return this.productId;
	}

}
