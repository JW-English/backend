package com.purut.api.auth;

import com.purut.api.auth.dto.AuthDtos.LoginRequest;
import com.purut.api.auth.dto.AuthDtos.OAuthLoginRequest;
import com.purut.api.auth.dto.AuthDtos.RefreshRequest;
import com.purut.api.auth.dto.AuthDtos.SignUpRequest;
import com.purut.api.auth.dto.AuthDtos.TokenResponse;
import com.purut.domain.user.Provider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final OAuthLoginService oAuthLoginService;

    public AuthController(AuthService authService,
                          RefreshTokenService refreshTokenService,
                          OAuthLoginService oAuthLoginService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.oAuthLoginService = oAuthLoginService;
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

    @PostMapping("/oauth/{provider}")
    @SecurityRequirements
    @Operation(summary = "소셜 로그인",
            description = "클라이언트가 각 사 SDK 로 받은 access_token 을 보내면 서버가 프로필을 직접 조회한다. 첫 로그인이 회원가입이다")
    public TokenResponse oauthLogin(@PathVariable Provider provider,
                                    @Valid @RequestBody OAuthLoginRequest request) {
        var result = oAuthLoginService.login(provider, request.accessToken());
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
