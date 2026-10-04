package com.nunegal.controller;

import com.nunegal.domain.ProductDetail;
import com.nunegal.service.SimilarProductsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves {@code GET /product/{productId}/similar}.
 */
@RestController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SimilarProductsController {

    private final SimilarProductsService service;

    /**
     * @param productId identifier of the product whose similar products are wanted
     * @return the similar products in the ranking the product API gave, possibly empty
     */
    @GetMapping("/product/{productId}/similar")
    public List<ProductDetail> findSimilarProducts(@PathVariable String productId) {
        return service.findSimilarProducts(productId);
    }

}