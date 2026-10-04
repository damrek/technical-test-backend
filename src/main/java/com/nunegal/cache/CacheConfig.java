package com.nunegal.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {
    public static final String PRODUCT_DETAIL_CACHE_NAME = "productDetail";
    public static final String SIMILAR_IDS_CACHE_NAME = "similarIds";

    private static final Duration PRODUCT_DETAIL_TTL = Duration.ofSeconds(10);
    private static final Duration SIMILAR_IDS_TTL = Duration.ofMinutes(5);

    private static final long MAX_ENTRIES = 10_000;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(PRODUCT_DETAIL_CACHE_NAME, SIMILAR_IDS_CACHE_NAME);

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES)
                .expireAfterWrite(PRODUCT_DETAIL_TTL));

        cacheManager.registerCustomCache(SIMILAR_IDS_CACHE_NAME, Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES)
                .expireAfterWrite(SIMILAR_IDS_TTL)
                .build());

        return cacheManager;
    }

}