package com.purut.api.config;

import com.purut.api.auth.JwtProperties;
import com.purut.api.auth.RefreshCookies;
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
