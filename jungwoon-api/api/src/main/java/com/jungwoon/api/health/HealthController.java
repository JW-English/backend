package com.jungwoon.api.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * P0 관통 확인용. 앱이 이 엔드포인트를 호출해 화면에 찍는다.
 * (심층 상태 점검은 /actuator/health 가 담당)
 */
@RestController
@RequestMapping("/api/public")
public class HealthController {

    private final String version;

    public HealthController(@Value("${jungwoon.version:dev}") String version) {
        this.version = version;
    }

    @GetMapping("/ping")
    @SecurityRequirements
    @Operation(summary = "서버 생존 확인", description = "인증 없이 호출 가능")
    public PingResponse ping() {
        return new PingResponse("jungwoon-api", version, Instant.now());
    }

    public record PingResponse(String service, String version, Instant serverTime) {
    }
}
