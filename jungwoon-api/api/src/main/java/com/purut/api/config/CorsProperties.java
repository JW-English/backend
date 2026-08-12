package com.purut.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "purut.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:3000");
        }
    }
}
