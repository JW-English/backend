package com.jungwoon.api.auth;

import com.jungwoon.api.auth.dto.AuthDtos.LoginRequest;
import com.jungwoon.api.auth.dto.AuthDtos.SignUpRequest;
import com.jungwoon.common.error.BusinessException;
import com.jungwoon.common.error.ErrorCode;
import com.jungwoon.domain.user.Role;
import com.jungwoon.domain.user.User;
import com.jungwoon.domain.user.UserRepository;
import com.jungwoon.infra.token.LoginAttemptStore;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptStore loginAttemptStore;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService,
                       LoginAttemptStore loginAttemptStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptStore = loginAttemptStore;
    }

    @Transactional
    public LoginResult signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 가입된 이메일입니다.");
        }

        User user = userRepository.save(User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(Role.STUDENT)
                .build());

        return new LoginResult(user, refreshTokenService.issue(user));
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        if (loginAttemptStore.isLocked(request.email())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);

        // 존재하지 않는 계정과 비밀번호 오류를 같은 응답으로 처리한다 — 가입 여부가 새면 계정 열거가 된다
        if (user == null
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptStore.recordFailure(request.email());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        loginAttemptStore.reset(request.email());
        user.markLoggedIn();

        return new LoginResult(user, refreshTokenService.issue(user));
    }

    @Transactional(readOnly = true)
    public User getUser(UserPrincipal principal) {
        return userRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
    }

    public record LoginResult(User user, TokenPair tokens) {
    }
}
