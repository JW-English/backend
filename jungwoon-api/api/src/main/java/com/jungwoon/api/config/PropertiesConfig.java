package com.jungwoon.api.config;

import com.jungwoon.api.auth.JwtProperties;
import com.jungwoon.api.auth.RefreshCookies;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        CorsProperties.class,
        JwtProperties.class,
        RefreshCookies.CookieProperties.class
})
public class PropertiesConfig {
}
