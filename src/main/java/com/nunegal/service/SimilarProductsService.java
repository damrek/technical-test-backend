package com.nunegal.service;

import com.nunegal.clients.productcatalog.ProductCatalogClient;
import com.nunegal.domain.ProductDetail;
import com.nunegal.exception.ProductNotFoundException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches the details of a product's similar products.
 *
 * <p>
 * Children run concurrently so a request costs its slowest child instead of the sum of all of them.
 * Any child that fails is dropped rather than failing the request; only a missing similar-id lookup
 * propagates, because then there is no answer left to degrade.
 */
@Slf4j
@Service
public class SimilarProductsService {

    private final ProductCatalogClient client;

    /**
     * Fixed size on purpose: the pool size is the ceiling on product calls in flight, and children
     * beyond it wait their turn instead of opening a connection each.
     */
    private final ExecutorService fanOutExecutor;

    public SimilarProductsService(ProductCatalogClient client,
                                  @Value("${api.similar.max-concurrent-product-api-calls:64}") int maxConcurrentProductApiCalls) {
        this.client = client;
        this.fanOutExecutor = Executors.newFixedThreadPool(maxConcurrentProductApiCalls);
    }

    /**
     * @param productId the product to look up
     * @return its similar products, in the order the product API ranked them
     * @throws ProductNotFoundException if the product API does not know this product
     */
    public List<ProductDetail> findSimilarProducts(String productId) {
        List<String> similarProductIds = client.findSimilarProductIds(productId);
        if (similarProductIds.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<Optional<ProductDetail>>> pendingDetails = new ArrayList<>(similarProductIds.size());
        for (String similarProductId : similarProductIds) {
            pendingDetails.add(CompletableFuture.supplyAsync(() -> fetchDetail(similarProductId), fanOutExecutor));
        }

        // Joined in submission order, not as answers arrive: the caller asked for a ranking, and
        // collecting by completion would return the same products wrongly shuffled.
        List<ProductDetail> details = new ArrayList<>(similarProductIds.size());
        for (CompletableFuture<Optional<ProductDetail>> pendingDetail : pendingDetails) {
            pendingDetail.join().ifPresent(details::add);
        }
        return List.copyOf(details);
    }

    /**
     * @param productId the similar product to fetch
     * @return its detail, or empty if this one child cannot produce one
     */
    private Optional<ProductDetail> fetchDetail(String productId) {
        try {
            return Optional.ofNullable(client.findProductDetail(productId));
        } catch (Exception ex) {
            log.warn("Omitting similar product {}: {}", productId, ex.toString());
            return Optional.empty();
        }
    }

    /**
     * Shutdown rather than close: in-flight children are already bounded by the read timeout.
     */
    @PreDestroy
    void shutdownFanOutExecutor() {
        fanOutExecutor.shutdown();
    }

}