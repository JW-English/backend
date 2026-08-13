package com.purut.infra.oauth;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;

/**
 * Apple·Google 이 주는 id_token(JWT) 검증기.
 *
 * 카카오처럼 access_token 을 제공자 API 에 되묻는 방식이 아니라, 토큰 자체의 서명을
 * 제공자 공개키(JWKS)로 검증한다. Apple 은 프로필 조회 API 자체가 없어서 이 방법뿐이다.
 *
 * <p>직접 구현하지 않고 {@link NimbusJwtDecoder} 에 맡긴 이유:
 * <ul>
 *   <li>JWKS 조회·캐시·키 회전 처리</li>
 *   <li>헤더의 alg 를 그대로 믿지 않는다 — alg 혼동 공격(예: RS256 을 none/HS256 으로
 *       바꿔치기)은 직접 짤 때 가장 흔히 빠뜨리는 구멍이다</li>
 * </ul>
 *
 * <p><b>aud 검증이 핵심이다.</b> 서명과 만료만 보면 <i>다른 앱</i>이 같은 제공자에서 받은
 * 토큰으로도 로그인이 된다. 우리 클라이언트 ID 로 발급된 토큰인지 반드시 확인해야 한다.
 */
public class OidcIdTokenVerifier {

    private final NimbusJwtDecoder decoder;
    private final String providerName;
    private final List<String> audiences;

    public OidcIdTokenVerifier(String providerName, String jwksUri, String issuer,
                               List<String> audiences) {
        this.providerName = providerName;
        this.audiences = audiences == null ? List.of() : audiences.stream().filter(a -> !a.isBlank()).toList();

        this.decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        this.decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                audienceValidator()));
    }

    public Jwt verify(String idToken) {
        // 설정이 비어 있으면 aud 를 못 거른다. 조용히 통과시키면 아무 앱의 토큰이나
        // 받아들이게 되므로, 설정 누락을 로그인 실패로 드러낸다
        if (audiences.isEmpty()) {
            throw new OAuth2ProfileException(
                    "%s 로그인이 아직 설정되지 않았습니다. (audiences 미지정)".formatted(providerName));
        }

        try {
            return decoder.decode(idToken);
        } catch (Exception e) {
            throw new OAuth2ProfileException("%s 토큰 검증에 실패했습니다.".formatted(providerName));
        }
    }

    private OAuth2TokenValidator<Jwt> audienceValidator() {
        return jwt -> {
            List<String> aud = jwt.getAudience();
            if (aud != null && aud.stream().anyMatch(audiences::contains)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_audience",
                    "이 앱에 발급된 토큰이 아닙니다.", null));
        };
    }
}
