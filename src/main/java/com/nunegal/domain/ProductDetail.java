package com.nunegal.domain;

import java.math.BigDecimal;

/**
 * Product detail, as declared by both OpenAPI contracts: {@code existingApis.yaml} for
 * {@code GET /product/{productId}}, and {@code similarProducts.yaml} for the items of the new
 * {@code GET /product/{productId}/similar} response. Both schemas declare exactly the same four
 * required properties, so one record serves as both the API response type and the item type
 * of the endpoint this service exposes.
 *
 * <p>
 * This is a record and not a Lombok {@code @Data} bean because these instances are cached
 * in-process. Immutability is the cache-safety property: a bean with setters would let any caller
 * corrupt a cached entry for every later request. A record needs no Jackson annotations either,
 * since its canonical constructor and accessors already match what the codecs need.
 *
 * @param id stable product identifier
 * @param name display name
 * @param price unit price, as {@link BigDecimal} so money never picks up binary floating-point
 * rounding
 * @param availability whether the product is currently in stock
 */
public record ProductDetail(String id, String name, BigDecimal price, boolean availability) {
}
