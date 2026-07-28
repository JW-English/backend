package com.jungwoon.infra.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Refresh Token 저장소 (Redis).
 *
 * 설계
 * - 토큰은 불투명 문자열이고 <b>Redis 가 진실</b>이다 → 서버에서 즉시 무효화할 수 있다.
 * - 로그인 1회 = 패밀리 1개. 회전(rotation)해도 패밀리는 유지된다.
 * - 이미 사용한 토큰이 다시 오면 탈취로 보고 <b>패밀리 전체</b>를 폐기한다.
 *
 * 키 구조
 *   rt:{token}          -> "{userId}:{familyId}"   (유효한 토큰)
 *   rt:used:{token}     -> "{familyId}"            (회전으로 소진된 토큰, 재사용 감지용)
 *   rt:family:{family}  -> Set<token>              (패밀리 일괄 폐기용)
 */
@Component
public class RefreshTokenStore {

    private static final String TOKEN_KEY = "rt:%s";
    private static final String USED_KEY = "rt:used:%s";
    private static final String FAMILY_KEY = "rt:family:%s";

    private final StringRedisTemplate redis;

    public RefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 저장된 토큰의 소유자·패밀리. 없으면 null. */
    public TokenRecord find(String token) {
        String value = redis.opsForValue().get(TOKEN_KEY.formatted(token));
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":", 2);
        return new TokenRecord(UUID.fromString(parts[0]), parts[1]);
    }

    /** 회전으로 이미 소진된 토큰인가 (= 탈취 의심). */
    public String findUsedFamily(String token) {
        return redis.opsForValue().get(USED_KEY.formatted(token));
    }

    public void save(String token, UUID userId, String familyId, Duration ttl) {
        redis.opsForValue().set(TOKEN_KEY.formatted(token), userId + ":" + familyId, ttl);
        String familyKey = FAMILY_KEY.formatted(familyId);
        redis.opsForSet().add(familyKey, token);
        redis.expire(familyKey, ttl);
    }

    /**
     * 회전: 기존 토큰을 소진 처리하고 새 토큰을 같은 패밀리로 발급한다.
     * 소진 마커는 원래 TTL 동안 남겨 재사용을 감지한다.
     */
    public void rotate(String oldToken, String newToken, UUID userId, String familyId, Duration ttl) {
        redis.delete(TOKEN_KEY.formatted(oldToken));
        redis.opsForValue().set(USED_KEY.formatted(oldToken), familyId, ttl);
        save(newToken, userId, familyId, ttl);
    }

    /** 패밀리 전체 폐기 — 재사용 감지 시, 그리고 로그아웃 시. */
    public void revokeFamily(String familyId) {
        String familyKey = FAMILY_KEY.formatted(familyId);
        Set<String> tokens = redis.opsForSet().members(familyKey);
        if (tokens != null) {
            tokens.forEach(token -> redis.delete(TOKEN_KEY.formatted(token)));
        }
        redis.delete(familyKey);
    }

    public record TokenRecord(UUID userId, String familyId) {
    }
}
