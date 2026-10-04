package com.nunegal.controller;

import com.nunegal.clients.productcatalog.ProductCatalogClient;
import com.nunegal.domain.ProductDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test for {@code GET /product/{productId}/similar}.
 *
 * <p>
 * MockMvc drives the real controller, service and JSON serialization without a socket, so the
 * product client is mocked and controls when each answer arrives.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SimilarProductsControllerTests {

    @MockitoBean
    private ProductCatalogClient client;

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsTheSimilarProductsInTheOrderTheApiGivesThem() throws Exception {
        when(client.findSimilarProductIds("1")).thenReturn(List.of("2", "3", "4"));
        // Slowest first, fastest last. These sleeps are what let the test fail: an implementation
        // collecting answers as they arrived would answer 4, 3, 2.
        when(client.findProductDetail("2")).thenAnswer(call -> {
            Thread.sleep(900);
            return detail("2");
        });
        when(client.findProductDetail("3")).thenAnswer(call -> {
            Thread.sleep(450);
            return detail("3");
        });
        when(client.findProductDetail("4")).thenReturn(detail("4"));

        mvc.perform(get("/product/{productId}/similar", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value("2"))
                .andExpect(jsonPath("$[1].id").value("3"))
                .andExpect(jsonPath("$[2].id").value("4"));
    }

    private static ProductDetail detail(String productId) {
        return new ProductDetail(productId, "Product " + productId, new BigDecimal("12.99"), true);
    }

}