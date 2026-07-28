package com.jungwoon.api.file;

import com.jungwoon.common.error.BusinessException;
import com.jungwoon.common.error.ErrorCode;
import com.jungwoon.infra.storage.FileStorage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 업로드용 presigned URL 발급.
 *
 * 흐름: presign → 클라이언트가 스토리지에 직접 PUT → 각 도메인 API 로 키 등록.
 * 서버는 바이트를 경유시키지 않는다.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/heic");

    private final FileStorage fileStorage;

    public FileController(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    @PostMapping("/presign")
    @Operation(summary = "업로드 URL 발급", description = "발급된 URL 로 클라이언트가 직접 PUT 한 뒤, 반환된 key 를 도메인 API 에 등록한다")
    public FileStorage.PresignedUpload presign(@Valid @RequestBody PresignRequest request) {
        if (!ALLOWED_TYPES.contains(request.contentType())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "지원하지 않는 이미지 형식입니다: " + request.contentType());
        }

        String key = fileStorage.newKey(request.directory(), request.extension());
        return fileStorage.presignUpload(key, request.contentType());
    }

    /**
     * 파일명은 받지 않는다 — 사용자 입력을 키에 넣으면 경로 조작·충돌 위험이 생긴다.
     * 서버가 UUID 로 키를 만든다.
     */
    public record PresignRequest(
            @NotBlank @Pattern(regexp = "homework|qna|avatar|listening", message = "허용되지 않은 저장 위치입니다")
            String directory,
            @NotBlank String contentType,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{1,5}") String extension
    ) {
    }
}
