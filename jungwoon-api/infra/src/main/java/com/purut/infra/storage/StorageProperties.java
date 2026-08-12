package com.purut.infra.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * S3 호환 스토리지 설정. 로컬은 MinIO, 운영은 Cloudflare R2.
 * 코드는 AWS SDK 하나로 동일하고 엔드포인트·키만 바뀐다.
 */
@ConfigurationProperties(prefix = "purut.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        /** presigned URL 유효시간. 짧을수록 안전하지만 업로드 중 만료되면 실패한다. */
        Duration uploadUrlTtl,
        Duration downloadUrlTtl,
        /** MinIO 는 가상 호스트 방식을 못 쓰므로 path-style 이 필요하다. */
        boolean pathStyleAccess
) {

    public StorageProperties {
        region = region != null ? region : "auto";
        uploadUrlTtl = uploadUrlTtl != null ? uploadUrlTtl : Duration.ofMinutes(10);
        downloadUrlTtl = downloadUrlTtl != null ? downloadUrlTtl : Duration.ofHours(1);
    }
}
