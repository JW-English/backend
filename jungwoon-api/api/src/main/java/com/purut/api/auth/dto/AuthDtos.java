package com.purut.api.auth.dto;

import com.purut.api.auth.TokenPair;
import com.purut.domain.user.Role;
import com.purut.domain.user.User;
import com.purut.domain.vocabulary.VocabLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** 인증 관련 요청·응답 DTO. Entity 를 그대로 직렬화하지 않는다. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignUpRequest(
            @Email(message = "이메일 형식이 올바르지 않습니다") @NotBlank String email,
            @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다") String password,
            @NotBlank String name
    ) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** 소셜 로그인: 클라이언트가 각 사 SDK 로 받은 access_token 만 보낸다. */
    public record OAuthLoginRequest(@NotBlank String accessToken) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            boolean onboardingRequired
    ) {
        public static TokenResponse of(TokenPair pair, User user) {
            return new TokenResponse(pair.accessToken(), pair.refreshToken(), pair.expiresIn(),
                    !user.isOnboarded());
        }
    }

    /**
     * 관리자 웹 응답. Refresh Token 이 없는 것이 핵심이다 —
     * 쿠키로만 전달해 JS 가 읽지 못하게 한다.
     */
    public record WebTokenResponse(String accessToken, long expiresIn) {
        public static WebTokenResponse of(TokenPair pair) {
            return new WebTokenResponse(pair.accessToken(), pair.expiresIn());
        }
    }

    /** 내 정보. 이름·전화번호 등 최소한만 내려보낸다. */
    public record MeResponse(
            UUID id,
            String email,
            String name,
            Role role,
            Integer grade,
            String school,
            /** 어휘 레벨. 학교 학년과 별개라 아직 지정되지 않았으면 null 이다 */
            VocabLevel vocabLevel,
            boolean onboarded
    ) {
        public static MeResponse of(User user) {
            return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                    user.getGrade(), user.getSchool(), user.getVocabLevel(), user.isOnboarded());
        }
    }
}
