package com.purut.infra.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 로그인 실패 카운터 (Redis).
 * 5회 실패 시 10분 잠금 — 무차별 대입을 늦춘다.
 */
@Component
public class LoginAttemptStore {

    private static final String KEY = "login:fail:%s";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    public LoginAttemptStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isLocked(String identifier) {
        String value = redis.opsForValue().get(KEY.formatted(identifier));
        return value != null && Integer.parseInt(value) >= MAX_ATTEMPTS;
    }

    /** 실패 1회 기록. 첫 실패에서 TTL 을 걸어 카운터가 영구히 남지 않게 한다. */
    public void recordFailure(String identifier) {
        String key = KEY.formatted(identifier);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, LOCK_DURATION);
        }
    }

    public void reset(String identifier) {
        redis.delete(KEY.formatted(identifier));
    }
}
