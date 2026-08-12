package com.purut.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 설정. 시크릿은 절대 application.yml 에 커밋하지 않고 환경변수로 주입한다.
 */
@ConfigurationProperties(prefix = "purut.jwt")
public record JwtProperties(
        String secret,
        Duration accessTtl,
        Duration refreshTtl
) {

    public JwtProperties {
        // HS256 은 최소 256bit 키를 요구한다. 짧은 키로 뜨면 운영에서 조용히 취약해진다.
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalStateException(
                    "purut.jwt.secret 은 32바이트 이상이어야 합니다. 환경변수 JWT_SECRET 을 확인하세요.");
        }
        accessTtl = accessTtl != null ? accessTtl : Duration.ofMinutes(30);
        refreshTtl = refreshTtl != null ? refreshTtl : Duration.ofDays(14);
    }
}
