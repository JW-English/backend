package com.purut.api.auth;

import com.purut.common.error.BusinessException;
import com.purut.common.error.ErrorCode;
import com.purut.domain.user.Role;
import com.purut.domain.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Access Token 발급·검증.
 *
 * Refresh Token 은 JWT 가 아니라 불투명(opaque) 문자열이고 Redis 가 진실이다
 * ({@link com.purut.api.auth.RefreshTokenService}). 서버 측 강제 로그아웃을 위해서다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_GRADE = "grade";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_GRADE, user.getGrade())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * 토큰을 검증하고 인증 주체를 만든다.
     * 서명·만료가 깨지면 예외 — 여기서 조용히 null 을 반환하면 인증 우회가 된다.
     */
    public UserPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new UserPrincipal(
                    UUID.fromString(claims.getSubject()),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    claims.get(CLAIM_GRADE, Integer.class));
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public long accessTtlSeconds() {
        return properties.accessTtl().toSeconds();
    }
}
