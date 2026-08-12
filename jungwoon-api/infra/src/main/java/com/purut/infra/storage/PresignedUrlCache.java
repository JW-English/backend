package com.purut.infra.storage;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 조회용 presigned URL 캐시.
 *
 * 매번 새로 서명하면 같은 파일인데도 URL 이 달라진다. 그러면 클라이언트가 URL 로
 * 캐시할 수 없어 <b>같은 듣기 문항을 다시 들을 때마다 3MB 를 새로 받는다.</b>
 * TTL 안에서는 같은 URL 을 돌려줘 HTTP 캐시가 동작하게 한다.
 *
 * 캐시 수명은 URL 수명보다 짧게 둔다. 같은 값이면 만료 직전 URL 을 받은 클라이언트가
 * 재생 도중 403 을 맞는다.
 */
@Component
public class PresignedUrlCache {

    private static final String KEY = "presign:get:%s";
    /** URL 이 이만큼은 남아 있는 상태로 나가야 한다. */
    private static final Duration SAFETY_MARGIN = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final Duration cacheTtl;

    public PresignedUrlCache(StringRedisTemplate redis, StorageProperties properties) {
        this.redis = redis;
        Duration ttl = properties.downloadUrlTtl().minus(SAFETY_MARGIN);
        // URL 수명이 여유분보다 짧으면 캐시하지 않는 편이 안전하다
        this.cacheTtl = ttl.isPositive() ? ttl : Duration.ZERO;
    }

    public String get(String storageKey, Supplier<String> presigner) {
        if (cacheTtl.isZero()) {
            return presigner.get();
        }

        String cacheKey = KEY.formatted(storageKey);
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String url = presigner.get();
        redis.opsForValue().set(cacheKey, url, cacheTtl);
        return url;
    }
}
