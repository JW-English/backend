package com.purut.api.auth;

import com.purut.common.error.BusinessException;
import com.purut.common.error.ErrorCode;
import com.purut.domain.user.User;
import com.purut.domain.user.UserRepository;
import com.purut.infra.token.RefreshTokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Refresh Token 발급·회전·폐기.
 *
 * 회전(Rotation)을 적용하고, 이미 소진된 토큰이 다시 오면 탈취로 간주해
 * 해당 로그인 세션(패밀리) 전체를 무효화한다.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenStore store;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final Duration refreshTtl;

    public RefreshTokenService(RefreshTokenStore store,
                               JwtTokenProvider tokenProvider,
                               UserRepository userRepository,
                               JwtProperties properties) {
        this.store = store;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.refreshTtl = properties.refreshTtl();
    }

    /** 로그인 성공 시 호출. 새 패밀리를 연다. */
    public TokenPair issue(User user) {
        String refreshToken = generateToken();
        String familyId = UUID.randomUUID().toString();
        store.save(refreshToken, user.getId(), familyId, refreshTtl);

        return new TokenPair(
                tokenProvider.createAccessToken(user),
                refreshToken,
                tokenProvider.accessTtlSeconds());
    }

    public TokenPair refresh(String refreshToken) {
        RefreshTokenStore.TokenRecord record = store.find(refreshToken);

        if (record == null) {
            // 이미 회전으로 소진된 토큰이 다시 왔다 = 탈취 의심 → 세션 전체를 끊는다
            String usedFamily = store.findUsedFamily(refreshToken);
            if (usedFamily != null) {
                store.revokeFamily(usedFamily);
                log.warn("Refresh Token 재사용 감지 — 패밀리 폐기 family={}", usedFamily);
                throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
            }
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 토큰이 유효해도 계정 상태가 진실이다. 탈퇴 사용자는 즉시 막는다.
        User user = userRepository.findById(record.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (!user.isActive()) {
            store.revokeFamily(record.familyId());
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        String newToken = generateToken();
        store.rotate(refreshToken, newToken, user.getId(), record.familyId(), refreshTtl);

        return new TokenPair(
                tokenProvider.createAccessToken(user),
                newToken,
                tokenProvider.accessTtlSeconds());
    }

    /** 로그아웃 — 해당 기기 세션(패밀리)만 끊는다. */
    public void revoke(String refreshToken) {
        RefreshTokenStore.TokenRecord record = store.find(refreshToken);
        if (record != null) {
            store.revokeFamily(record.familyId());
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
