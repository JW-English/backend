package com.jungwoon.api.auth;

import com.jungwoon.api.auth.dto.AuthDtos.LoginRequest;
import com.jungwoon.api.auth.dto.AuthDtos.WebTokenResponse;
import com.jungwoon.common.error.BusinessException;
import com.jungwoon.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 웹 전용 인증.
 *
 * 모바일은 Bearer 헤더 방식({@link AuthController})을 쓰고, 웹은 여기를 쓴다.
 * 차이는 Refresh Token 을 <b>응답 본문 대신 httpOnly 쿠키</b>로 준다는 것뿐이다.
 * 브라우저에서 Refresh Token 을 JS 가 읽을 수 있으면 XSS 한 번에 세션이 넘어간다.
 */
@RestController
@RequestMapping("/api/auth/web")
public class WebAuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookies refreshCookies;

    public WebAuthController(AuthService authService,
                             RefreshTokenService refreshTokenService,
                             RefreshCookies refreshCookies) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "관리자 웹 로그인",
            description = "Refresh 는 httpOnly 쿠키로 내려간다. Access 는 본문으로 주며 클라이언트는 메모리에만 보관한다")
    public ResponseEntity<WebTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        var result = authService.login(request);

        // 관리자 웹은 선생님·관리자만 쓴다. 학생 계정으로는 들어올 수 없다
        if (!result.user().getRole().isStaff()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 없는 계정입니다.");
        }

        return withRefreshCookie(refreshCookies.create(result.tokens().refreshToken()),
                WebTokenResponse.of(result.tokens()));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "토큰 재발급", description = "쿠키의 Refresh Token 을 회전시킨다")
    public ResponseEntity<WebTokenResponse> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        var tokens = refreshTokenService.refresh(refreshToken);
        return withRefreshCookie(refreshCookies.create(tokens.refreshToken()),
                WebTokenResponse.of(tokens));
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "로그아웃", description = "서버 세션을 폐기하고 쿠키를 만료시킨다")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.expire().toString())
                .build();
    }

    private <T> ResponseEntity<T> withRefreshCookie(ResponseCookie cookie, T body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }
}
