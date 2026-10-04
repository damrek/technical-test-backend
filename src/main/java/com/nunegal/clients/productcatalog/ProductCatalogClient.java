package com.nunegal.clients.productcatalog;

import java.util.List;

import com.nunegal.domain.ProductDetail;
import com.nunegal.exception.ProductNotFoundException;

/**
 * Read access to the two product APIs declared in {@code existingApis.yaml}.
 *
 * <p>
 * The interface exists so the transport stays behind a seam: the cache and the concurrency bound
 * can wrap it without the aggregation service knowing how the calls are made.
 *
 * <p>
 * Neither method retries. Those failures are permanent (a 404 that never becomes a 200, a
 * 500 on {@code /product/6}), so a retry only spends the request's remaining time budget on an
 * answer that cannot change.
 */
public interface ProductCatalogClient {

	/**
	 * Identifiers of the products similar to the given one, in the API's similarity order.
	 * @param productId identifier of the product to look up
	 * @return the similar product identifiers, never {@code null}, possibly empty
	 * @throws ProductNotFoundException if the product API does not know this product
	 */
	List<String> findSimilarProductIds(String productId);

	/**
	 * Full detail of a single product.
	 * @param productId identifier of the product to look up
	 * @return the product detail, never {@code null}
	 * @throws ProductNotFoundException if the product API does not know this product
	 */
	ProductDetail findProductDetail(String productId);

}
