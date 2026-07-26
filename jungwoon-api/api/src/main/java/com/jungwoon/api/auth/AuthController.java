package com.jungwoon.api.auth;

import com.jungwoon.api.auth.dto.AuthDtos.LoginRequest;
import com.jungwoon.api.auth.dto.AuthDtos.RefreshRequest;
import com.jungwoon.api.auth.dto.AuthDtos.SignUpRequest;
import com.jungwoon.api.auth.dto.AuthDtos.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/signup")
    @SecurityRequirements
    @Operation(summary = "이메일 회원가입")
    public ResponseEntity<TokenResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        var result = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TokenResponse.of(result.tokens(), result.user()));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "이메일 로그인")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        var result = authService.login(request);
        return TokenResponse.of(result.tokens(), result.user());
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "토큰 재발급", description = "회전 방식. 이미 쓴 토큰을 다시 보내면 세션 전체가 폐기된다")
    public TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
        return refreshTokenService.refresh(request.refreshToken());
    }

    /**
     * 로그아웃은 인증을 요구하지 않는다 — Access Token 이 이미 만료된 상태에서도
     * Refresh 세션은 끊을 수 있어야 하기 때문이다.
     */
    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "로그아웃", description = "해당 기기의 Refresh Token 세션을 폐기한다")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
