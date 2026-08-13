package com.purut.api.auth;

import com.purut.common.error.BusinessException;
import com.purut.common.error.ErrorCode;
import com.purut.domain.user.Provider;
import com.purut.domain.user.Role;
import com.purut.domain.user.SocialAccount;
import com.purut.domain.user.SocialAccountRepository;
import com.purut.domain.user.User;
import com.purut.domain.user.UserRepository;
import com.purut.infra.oauth.OAuth2UserInfo;
import com.purut.infra.oauth.OAuth2UserInfoClientRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인. 첫 로그인이 곧 회원가입이다.
 *
 * 서버가 access_token 으로 프로필을 <b>직접</b> 조회하므로 클라이언트가 보낸 이메일·이름은
 * 쓰지 않는다.
 */
@Service
public class OAuthLoginService {

    private final OAuth2UserInfoClientRegistry clientRegistry;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    public OAuthLoginService(OAuth2UserInfoClientRegistry clientRegistry,
                             SocialAccountRepository socialAccountRepository,
                             UserRepository userRepository,
                             RefreshTokenService refreshTokenService) {
        this.clientRegistry = clientRegistry;
        this.socialAccountRepository = socialAccountRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthService.LoginResult login(Provider provider, String credential, String displayName) {
        OAuth2UserInfo info = clientRegistry.get(provider).fetch(credential);

        User user = socialAccountRepository
                .findByProviderAndProviderId(provider, info.providerId())
                .map(SocialAccount::getUser)
                .orElseGet(() -> register(provider, info, displayName));

        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }
        user.markLoggedIn();

        return new AuthService.LoginResult(user, refreshTokenService.issue(user));
    }

    private User register(Provider provider, OAuth2UserInfo info, String displayName) {
        // 같은 이메일로 다른 제공자에 이미 가입돼 있으면 자동 연동하지 않는다.
        // 이메일 소유권을 검증하지 않은 자동 연동은 계정 탈취 경로가 된다.
        if (info.email() != null && userRepository.existsByEmail(info.email())) {
            throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED,
                    "이미 다른 방법으로 가입된 계정입니다. 기존 방법으로 로그인해 주세요.");
        }

        User user = userRepository.save(User.builder()
                .email(info.email())
                .name(resolveName(info.nickname(), displayName))
                .role(Role.STUDENT)
                .build());

        socialAccountRepository.save(new SocialAccount(user, provider, info.providerId()));
        return user;
    }
    /**
     * 표시 이름을 정한다.
     *
     * 제공자가 준 이름을 우선한다. Apple 은 identity token 에 이름이 없어 앱이 보낸 값을
     * 쓰는데, 클라이언트가 보낸 값이라 신원 판단에는 쓰지 않는다 — 화면에 뜨는 기본값일 뿐이고
     * 사용자가 온보딩에서 바꾼다.
     */
    private String resolveName(String fromProvider, String fromClient) {
        if (fromProvider != null && !fromProvider.isBlank()) {
            return fromProvider;
        }
        if (fromClient != null && !fromClient.isBlank()) {
            return fromClient.strip();
        }
        return "학생";
    }
}
