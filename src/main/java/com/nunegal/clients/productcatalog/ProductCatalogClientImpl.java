package com.nunegal.clients.productcatalog;

import com.nunegal.domain.ProductDetail;
import com.nunegal.exception.ProductNotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static com.nunegal.cache.CacheConfig.PRODUCT_DETAIL_CACHE_NAME;
import static com.nunegal.cache.CacheConfig.SIMILAR_IDS_CACHE_NAME;

/**
 * {@link ProductCatalogClient} over a {@link RestClient} pointed at the product APIs.
 *
 * <p>
 * The only translation this layer performs is turning a 404 from the product API into
 * {@link ProductNotFoundException}. Everything else, including a read timeout or a 5xx, is left to
 * propagate: the service above decides how to degrade on those, and this layer hiding them would
 * leave it unable to see what actually happened.
 */
@Component
public class ProductCatalogClientImpl implements ProductCatalogClient {

    private static final ParameterizedTypeReference<List<String>> SIMILAR_IDS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public ProductCatalogClientImpl(RestClient apiProductsRestClient) {
        this.restClient = apiProductsRestClient;
    }

    // sync = true locks the key so concurrent first-callers queue instead of all missing and
    // stampeding the product API, which is the failure a cache is meant to prevent.
    @Override
    @Cacheable(cacheNames = SIMILAR_IDS_CACHE_NAME, key = "#productId", sync = true)
    public List<String> findSimilarProductIds(String productId) {
        try {
            List<String> productIds = restClient.get()
                    .uri("/product/{productId}/similarids", productId)
                    .retrieve()
                    .body(SIMILAR_IDS);
            // Copied so the caller cannot mutate what was just decoded.
            return (productIds != null) ? List.copyOf(productIds) : List.of();
        } catch (RestClientResponseException ex) {
            throw translateNotFound(ex, productId);
        }
    }

    @Override
    @Cacheable(cacheNames = PRODUCT_DETAIL_CACHE_NAME, key = "#productId", sync = true)
    public ProductDetail findProductDetail(String productId) {
        try {
            ProductDetail detail = restClient.get()
                    .uri("/product/{productId}", productId)
                    .retrieve()
                    .body(ProductDetail.class);
            if (detail == null) {
                throw new ProductNotFoundException(productId);
            }
            return detail;
        } catch (RestClientResponseException ex) {
            throw translateNotFound(ex, productId);
        }
    }

    private RuntimeException translateNotFound(RestClientResponseException ex, String productId) {
        if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            return new ProductNotFoundException(productId);
        }
        return ex;
    }

}
