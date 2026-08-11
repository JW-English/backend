package com.purut.api.user;

import com.purut.api.auth.AuthService;
import com.purut.api.auth.UserPrincipal;
import com.purut.api.auth.dto.AuthDtos.MeResponse;
import com.purut.api.user.dto.MeDtos.PasswordChangeRequest;
import com.purut.api.user.dto.MeDtos.Summary;
import com.purut.api.user.dto.OnboardingRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @GetMapping("/summary")
    @Operation(summary = "마이페이지 요약",
            description = "숙제 제출률과 단어시험 성적. 집계 쿼리 2번으로 끝난다")
    public Summary summary(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.summary(principal);
    }

    @PutMapping("/password")
    @Operation(summary = "비밀번호 변경",
            description = "현재 비밀번호를 확인한다. 소셜 전용 계정은 400")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(principal, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "회원탈퇴",
            description = "소프트 삭제. 이후 로그인·토큰 갱신이 막힌다")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal UserPrincipal principal) {
        userService.withdraw(principal);
        return ResponseEntity.noContent().build();
    }
}
