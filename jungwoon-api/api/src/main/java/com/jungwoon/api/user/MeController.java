package com.jungwoon.api.user;

import com.jungwoon.api.auth.AuthService;
import com.jungwoon.api.auth.UserPrincipal;
import com.jungwoon.api.auth.dto.AuthDtos.MeResponse;
import com.jungwoon.api.user.dto.OnboardingRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증이 필요한 경로. 인증 체인이 실제로 동작하는지 확인하는 기준점이기도 하다. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthService authService;
    private final UserService userService;

    public MeController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "내 정보 조회")
    public MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return MeResponse.of(authService.getUser(principal));
    }

    @PutMapping("/onboarding")
    @Operation(summary = "프로필 설정",
            description = "학년은 단어 DAY 가 열리는 기준이다. 진급 시 다시 호출해 수정한다")
    public MeResponse onboarding(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody OnboardingRequest request) {
        return MeResponse.of(userService.completeOnboarding(principal, request));
    }
}
