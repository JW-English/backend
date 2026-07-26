package com.jungwoon.api.user;

import com.jungwoon.api.auth.AuthService;
import com.jungwoon.api.auth.UserPrincipal;
import com.jungwoon.api.auth.dto.AuthDtos.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증이 필요한 경로. 인증 체인이 실제로 동작하는지 확인하는 기준점이기도 하다. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    @Operation(summary = "내 정보 조회")
    public MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return MeResponse.of(authService.getUser(principal));
    }
}
