package com.purut.infra.oauth;

import com.purut.domain.user.Provider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 구글 로그인. 클라이언트가 받은 <b>id_token</b> 을 검증한다.
 *
 * userinfo 엔드포인트를 호출하는 방법도 있지만 id_token 검증이 낫다.
 * 네트워크 왕복이 없고(JWKS 는 캐시된다), 토큰이 우리 앱에 발급된 것인지(aud)까지
 * 한 번에 확인된다.
 *
 * <p>이메일은 email_verified 가 true 일 때만 쓴다. 구글은 조직 계정 등에서
 * 미검증 이메일을 줄 수 있는데, 그걸 그대로 받으면 남의 이메일로 가입하는 경로가 열린다.
 */
@Component
public class GoogleUserInfoClient implements OAuth2UserInfoClient {

    private final OidcIdTokenVerifier verifier;

    public GoogleUserInfoClient(OAuthProviderProperties properties) {
        var google = properties.google();
        this.verifier = new OidcIdTokenVerifier(
                "구글", google.jwksUri(), google.issuer(), google.audiences());
    }

    @Override
    public Provider provider() {
        return Provider.GOOGLE;
    }

    @Override
    public OAuth2UserInfo fetch(String idToken) {
        Jwt jwt = verifier.verify(idToken);

        String providerId = jwt.getSubject();
        if (providerId == null || providerId.isBlank()) {
            throw new OAuth2ProfileException("구글 토큰에 사용자 식별자가 없습니다.");
        }

        String email = Boolean.TRUE.equals(jwt.getClaim("email_verified"))
                ? jwt.getClaimAsString("email")
                : null;

        return new OAuth2UserInfo(providerId, email, jwt.getClaimAsString("name"));
    }
}
