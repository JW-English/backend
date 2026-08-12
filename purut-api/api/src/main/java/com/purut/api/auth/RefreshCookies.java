package com.purut.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 관리자 웹용 Refresh Token 쿠키.
 *
 * 왜 쿠키인가 — 브라우저에서 Refresh Token 을 localStorage 에 두면 XSS 한 방에 털린다.
 * httpOnly 쿠키는 JS 가 읽을 수 없다. Access Token 은 메모리에만 둔다.
 *
 * CSRF 는 SameSite=Strict 로 막는다. 공격자 사이트에서 보낸 요청에는 쿠키가 실리지 않는다.
 * 이 전제가 성립하려면 관리자 웹과 API 가 <b>같은 사이트</b>(등록 가능 도메인이 동일)여야 한다.
 * 운영에서도 admin.example.com / api.example.com 처럼 한 도메인 아래에 둘 것.
 */
@Component
public class RefreshCookies {

    public static final String NAME = "refresh_token";

    /** 쿠키가 필요한 경로에만 실어 보낸다 — 다른 API 요청에는 붙지 않는다. */
    private static final String PATH = "/api/auth";

    private final boolean secure;
    private final Duration ttl;

    public RefreshCookies(CookieProperties properties, JwtProperties jwtProperties) {
        this.secure = properties.secure();
        this.ttl = jwtProperties.refreshTtl();
    }

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken).maxAge(ttl).build();
    }

    /** 로그아웃 — 같은 속성으로 만료시켜야 브라우저가 지운다. */
    public ResponseCookie expire() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }

    @ConfigurationProperties(prefix = "purut.auth.cookie")
    public record CookieProperties(boolean secure) {
    }
}
