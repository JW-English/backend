package com.jungwoon.api.config;

import com.jungwoon.api.auth.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({CorsProperties.class, JwtProperties.class})
public class PropertiesConfig {
}
