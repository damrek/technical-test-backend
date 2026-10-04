package com.nunegal.clients.productcatalog;

import com.nunegal.domain.ProductDetail;
import com.nunegal.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Tests for {@link ProductCatalogClient}, against a stubbed HTTP exchange.
 *
 * <p>
 * {@link MockRestServiceServer} replaces the request factory, so these tests cover URI building,
 * decoding and status translation without a socket.
 */
class ProductCatalogClientImplTests {

    private static final String BASE_URL = "http://localhost:3001";

    private MockRestServiceServer productApi;

    private ProductCatalogClient client;

    @BeforeEach
    void useAStubbedProductApi() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        productApi = MockRestServiceServer.bindTo(builder).build();
        client = new ProductCatalogClientImpl(builder.build());
    }

    @Test
    void findsTheSimilarProductIdsInTheOrderTheApiGivesThem() {
        productApi.expect(requestTo(BASE_URL + "/product/1/similarids"))
                .andRespond(withSuccess("""
                        ["2","3","4"]""", MediaType.APPLICATION_JSON));

        assertThat(client.findSimilarProductIds("1")).containsExactly("2", "3", "4");
    }

    @Test
    void findsTheProductDetail() {
        productApi.expect(requestTo(BASE_URL + "/product/2"))
                .andRespond(withSuccess("""
                        {"id":"2","name":"Product 2","price":12.99,"availability":true}""", MediaType.APPLICATION_JSON));

        assertThat(client.findProductDetail("2"))
                .isEqualTo(new ProductDetail("2", "Product 2", new BigDecimal("12.99"), true));
    }

    @Test
    void acceptsAnEmptySimilarIdList() {
        // similarProducts.yaml sets minItems: 0, so a product with no similar ones is a normal
        // answer rather than an error, and it must not surface as null.
        productApi.expect(requestTo(BASE_URL + "/product/9/similarids"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.findSimilarProductIds("9")).isEmpty();
    }

    @Test
    void callsTheDocumentedApiPaths() {
        productApi.expect(requestTo(BASE_URL + "/product/1/similarids"))
                .andRespond(withSuccess("""
                        ["2"]""", MediaType.APPLICATION_JSON));
        productApi.expect(requestTo(BASE_URL + "/product/2"))
                .andRespond(withSuccess("""
                        {"id":"2","name":"Product 2","price":12.99,"availability":true}""", MediaType.APPLICATION_JSON));

        client.findSimilarProductIds("1");
        client.findProductDetail("2");

        // Every expectation was addressed by URL, and nothing else was called.
        productApi.verify();
    }

    @Test
    void reportsMissingSimilarIdsAsNotFound() {
        productApi.expect(requestTo(BASE_URL + "/product/404/similarids"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("Product not found"));

        assertThat(catchThrowable(() -> client.findSimilarProductIds("404")))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void reportsAMissingProductDetailAsNotFound() {
        productApi.expect(requestTo(BASE_URL + "/product/404"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("Product not found"));

        assertThat(catchThrowable(() -> client.findProductDetail("404")))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void propagatesAServerErrorWithoutDisguisingItAsNotFound() {
        // /product/6 always answers 500 upstream. The aggregation has to tell that apart from a
        // 404, because only one of the two means "omit this child and carry on", and collapsing
        // them into a single exception type would make that decision impossible.
        productApi.expect(requestTo(BASE_URL + "/product/6")).andRespond(withServerError());

        Throwable thrown = catchThrowable(() -> client.findProductDetail("6"));

        assertThat(thrown).isInstanceOf(RestClientResponseException.class)
                .isNotInstanceOf(ProductNotFoundException.class);
        assertThat(((RestClientResponseException) thrown).getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void keepsATransportFailureSeparateFromAServerError() {
        // A stalled body raises a transport failure carrying no status, so it cannot be told apart
        // from a 404 by type. Only catching the not-found case would let this escape and fail a
        // request whose other children are perfectly serviceable.
        productApi.expect(requestTo(BASE_URL + "/product/10000"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        Throwable thrown = catchThrowable(() -> client.findProductDetail("10000"));

        assertThat(thrown).isInstanceOf(RestClientException.class);
        // The distinction the aggregation depends on: a transport failure is NOT status-bearing, so
        // it stays separable from the 5xx case above, which is a RestClientResponseException.
        assertThat(thrown).isNotInstanceOf(RestClientResponseException.class);
        assertThat(thrown.getCause()).isInstanceOf(SocketTimeoutException.class);
    }

}