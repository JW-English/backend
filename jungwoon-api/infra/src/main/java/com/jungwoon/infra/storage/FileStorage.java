package com.jungwoon.infra.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 파일 업로드·조회.
 *
 * 서버는 바이트를 경유시키지 않는다 — presigned URL 을 발급하고 클라이언트가 스토리지에
 * 직접 올린다. 서버 대역폭·메모리를 아끼기 위해서다.
 *
 * 키는 UUID 기반이라 추측할 수 없다. 비공개 버킷 + 만료형 URL 과 합쳐 보호한다.
 */
@Component
public class FileStorage {

    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final StorageProperties properties;
    private final PresignedUrlCache urlCache;

    public FileStorage(S3Client s3Client, S3Presigner presigner, StorageProperties properties,
                       PresignedUrlCache urlCache) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.properties = properties;
        this.urlCache = urlCache;
    }

    /**
     * 업로드용 키를 만든다. 확장자만 클라이언트 입력을 받고 파일명은 쓰지 않는다 —
     * 사용자 입력 문자열을 키에 넣으면 경로 조작·충돌 위험이 생긴다.
     */
    public String newKey(String directory, String extension) {
        String safeExtension = extension == null || extension.isBlank()
                ? "bin"
                : extension.toLowerCase().replaceAll("[^a-z0-9]", "");
        return "%s/%s/%s.%s".formatted(
                directory, LocalDate.now().format(DATE_PREFIX), UUID.randomUUID(), safeExtension);
    }

    public PresignedUpload presignUpload(String key, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build();

        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(objectRequest)
                .build());

        return new PresignedUpload(key, presigned.url().toString(),
                properties.uploadUrlTtl().toSeconds());
    }

    /**
     * 열람용 URL. 만료가 있으므로 DB 에는 키만 저장하고 조회 시점에 만든다.
     *
     * TTL 안에서는 캐시된 같은 URL 을 돌려준다 — 매번 새로 서명하면 클라이언트가
     * 캐시를 못 해 같은 음원을 계속 다시 받는다.
     */
    public String presignDownload(String key) {
        return urlCache.get(key, () -> presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(properties.downloadUrlTtl())
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(properties.bucket())
                                .key(key)
                                .build())
                        .build())
                .url()
                .toString());
    }

    /**
     * 클라이언트가 "올렸다"고 말한 키가 실제로 존재하는지 확인한다.
     * 이 검증이 없으면 올리지도 않은 키를 등록해 빈 제출물을 만들 수 있다.
     */
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    public record PresignedUpload(String key, String url, long expiresIn) {
    }
}
