package com.nunegal.clients.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the {@link org.springframework.web.client.RestClient} used to reach the product APIs.
 *
 * <p>
 * Connection settings and the request factory come from {@code spring.http.clients.*}, so this only
 * contributes what is specific to this dependency: where it lives. Giving the product API its own
 * client keeps its base URL from leaking into, or being leaked by, any other outbound call.
 */
@Configuration(proxyBeanMethods = false)
class ApiClientConfig {

	@Bean
	org.springframework.web.client.RestClient apiProductsRestClient(
			org.springframework.web.client.RestClient.Builder builder,
			@Value("${api.products.base-url}") String baseUrl) {
		return builder.baseUrl(withoutTrailingSlash(baseUrl)).build();
	}

	/**
	 * Product API URIs are declared as {@code /product/{productId}}, so a base URL ending in a slash
	 * would be joined into {@code //product/{productId}}.
	 */
	private static String withoutTrailingSlash(String baseUrl) {
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

}