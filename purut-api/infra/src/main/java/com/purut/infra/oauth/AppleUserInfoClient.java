package com.purut.infra.oauth;

import com.purut.domain.user.Provider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Sign in with Apple. 클라이언트가 받은 <b>identity token</b> 을 검증한다.
 *
 * Apple 은 프로필 조회 API 를 제공하지 않는다. 토큰 안에 든 것이 전부다.
 *
 * <p><b>이름이 없다.</b> Apple 은 identity token 에 이름을 넣지 않고, 최초 인증
 * 응답의 별도 필드로 <i>딱 한 번만</i> 준다. 두 번째 로그인부터는 어떤 경로로도 받을 수
 * 없다. 그래서 이 클라이언트는 nickname 을 항상 null 로 돌려주고, 이름은 앱이
 * 최초 로그인 때 함께 보낸 값을 쓴다(신원 판단에는 쓰지 않고 표시용 기본값으로만).
 *
 * <p>이메일 가리기를 선택하면 {@code @privaterelay.appleid.com} 주소가 온다.
 * 정상 이메일로 취급하되, 우리가 보낸 메일이 사용자에게 닿지 않을 수 있다.
 */
@Component
public class AppleUserInfoClient implements OAuth2UserInfoClient {

    private final OidcIdTokenVerifier verifier;

    public AppleUserInfoClient(OAuthProviderProperties properties) {
        var apple = properties.apple();
        this.verifier = new OidcIdTokenVerifier(
                "Apple", apple.jwksUri(), apple.issuer(), apple.audiences());
    }

    @Override
    public Provider provider() {
        return Provider.APPLE;
    }

    @Override
    public OAuth2UserInfo fetch(String identityToken) {
        Jwt jwt = verifier.verify(identityToken);

        String providerId = jwt.getSubject();
        if (providerId == null || providerId.isBlank()) {
            throw new OAuth2ProfileException("Apple 토큰에 사용자 식별자가 없습니다.");
        }

        // Apple 은 email_verified 를 문자열 "true" 로 줄 때가 있다
        Object verified = jwt.getClaim("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified));
        String email = emailVerified ? jwt.getClaimAsString("email") : null;

        return new OAuth2UserInfo(providerId, email, null);
    }
}
