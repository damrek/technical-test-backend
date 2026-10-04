package com.nunegal.clients.productcatalog;

import com.nunegal.domain.ProductDetail;
import com.nunegal.exception.ProductNotFoundException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * {@link ProductCatalogClient} over a {@link RestClient} pointed at the product APIs.
 *
 * <p>
 * The only translation this layer performs is turning a 404 from the product API into
 * {@link ProductNotFoundException}. Everything else, including a read timeout or a 5xx, is left to
 * propagate: the resilience layer owns how those are bounded and retried, and hiding them here
 * would leave that layer unable to see what actually happened.
 */
@Component
public class ProductCatalogClientImpl implements ProductCatalogClient {

    private static final ParameterizedTypeReference<List<String>> SIMILAR_IDS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public ProductCatalogClientImpl(RestClient apiProductsRestClient) {
        this.restClient = apiProductsRestClient;
    }

    @Override
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
