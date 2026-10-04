package com.nunegal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contract tests for {@link ProductDetail}.
 *
 * <p>
 * This record is read from {@code GET /product/{productId}} on the product API and is also the
 * item type returned by the new {@code GET /product/{productId}/similar} endpoint, so it is
 * bound in both directions and pinned here against both OpenAPI contracts.
 */
class ProductDetailTest {

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	@Test
	void deserializesTheProductDetailBody() {
		// The body shape declared by existingApis.yaml -> /product/{productId}.
		String body = """
				{"id":"1","name":"Product 1","price":12.99,"availability":true}""";

		ProductDetail detail = objectMapper.readValue(body, ProductDetail.class);

		assertThat(detail.id()).isEqualTo("1");
		assertThat(detail.name()).isEqualTo("Product 1");
		// Compared numerically, not by equals: scale must not depend on the wire format.
		assertThat(detail.price()).isEqualByComparingTo("12.99");
		assertThat(detail.availability()).isTrue();
	}

	@Test
	void serializesExactlyTheContractProperties() {
		// similarProducts.yaml requires id, name, price and availability. No more, no fewer.
		ProductDetail detail = new ProductDetail("1", "Product 1", new BigDecimal("12.99"), true);

		String json = objectMapper.writeValueAsString(detail);

		Map<String, Object> fields = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
		});
		assertThat(fields).containsOnlyKeys("id", "name", "price", "availability");
		// toString() so the assertion holds whether the untyped number binds to BigDecimal or Double.
		assertThat(fields.get("price").toString()).isEqualTo("12.99");
	}

	@Test
	void exposesNoSetters() {
		// These instances are cached in-process. A single setter would let any caller corrupt the
		// cached value for every subsequent request, so immutability is a cache-safety property
		// here, not a style preference.
		assertThat(Arrays.stream(ProductDetail.class.getMethods())
				.filter(method -> method.getName().startsWith("set")))
				.isEmpty();
	}

	@Test
	void comparesByValue() {
		ProductDetail one = new ProductDetail("1", "Product 1", new BigDecimal("12.99"), true);
		ProductDetail same = new ProductDetail("1", "Product 1", new BigDecimal("12.99"), true);
		ProductDetail other = new ProductDetail("2", "Product 2", new BigDecimal("12.99"), true);

		assertThat(one).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other);
	}

}
