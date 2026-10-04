package com.nunegal.clients.productcatalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.nunegal.cache.CacheConfig.PRODUCT_DETAIL_CACHE_NAME;
import static com.nunegal.cache.CacheConfig.SIMILAR_IDS_CACHE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Each test queues exactly one HTTP response per request that should reach the product API, then
 * repeats the reads. A second round-trip finds no response waiting, so it fails. The only way to
 * pass is a cache hit returning the right value.
 */
@SpringBootTest
class ProductCatalogClientCachingTests {

    private static final String BASE_URL = "http://localhost:3001";

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final String PRODUCT_2 = """
            {"id":"2","name":"Product 2","price":12.99,"availability":true}""";

    private static final String PRODUCT_3 = """
            {"id":"3","name":"Product 3","price":7.50,"availability":false}""";

    private static MockRestServiceServer productApi;

    @Autowired
    private ProductCatalogClient client;

    @Autowired
    private CacheManager cacheManager;

    @TestConfiguration
    static class StubbedTransport {

        /**
         * Replaces Boot's builder, so ApiClientConfig builds its RestClient over the stub.
         */
        @Bean
        RestClient.Builder stubbedProductApiBuilder() {
            RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
            productApi = MockRestServiceServer.bindTo(builder).build();
            return builder;
        }
    }

    @BeforeEach
    void startFromEmptyCaches() {
        // The context outlives each test, so a cached entry or an already-served request would leak
        // into the next one.
        cacheManager.getCache(PRODUCT_DETAIL_CACHE_NAME).clear();
        cacheManager.getCache(SIMILAR_IDS_CACHE_NAME).clear();
        productApi.reset();
    }

    @Test
    void servesRepeatedReadsFromCache() {
        productApi.expect(requestTo(BASE_URL + "/product/2")).andRespond(withSuccess(PRODUCT_2, JSON));
        productApi.expect(requestTo(BASE_URL + "/product/3")).andRespond(withSuccess(PRODUCT_3, JSON));
        productApi.expect(requestTo(BASE_URL + "/product/1/similarids")).andRespond(withSuccess("[\"2\",\"3\"]", JSON));

        assertThat(client.findProductDetail("2").id()).isEqualTo("2");
        assertThat(client.findProductDetail("2").id()).isEqualTo("2");

        // A different product still reaches the API, so the cache is keyed per product rather than
        // answering every detail request with one shared entry.
        assertThat(client.findProductDetail("3").id()).isEqualTo("3");

        assertThat(client.findSimilarProductIds("1")).containsExactly("2", "3");
        assertThat(client.findSimilarProductIds("1")).containsExactly("2", "3");

        // Five reads consumed exactly three responses: two hits per cached key.
        productApi.verify();
    }

    @Test
    void keepsPricesShortLivedAndTheSimilarityGraphLongLived() {
        // The test above would pass if either number changed, because it never waits for an entry to
        // age out. These read the policy each cache was actually built with.
        assertThat(expiryOf(PRODUCT_DETAIL_CACHE_NAME)).isEqualTo(Duration.ofSeconds(10));
        assertThat(expiryOf(SIMILAR_IDS_CACHE_NAME)).isEqualTo(Duration.ofMinutes(5));
    }

    /**
     * Also pins the size, so a capacity change cannot pass unnoticed either.
     */
    private Duration expiryOf(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        assertThat(cache).isInstanceOf(CaffeineCache.class);

        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();

        assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(10_000L);
        return nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter();
    }

}