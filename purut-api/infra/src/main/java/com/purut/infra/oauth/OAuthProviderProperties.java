package com.purut.infra.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 소셜 제공자 설정.
 *
 * audiences 는 우리 앱의 클라이언트 ID 다. 제공자마다 값이 다르다.
 * <ul>
 *   <li>Apple — 네이티브 로그인은 번들 ID(com.purut), 웹 흐름은 Service ID</li>
 *   <li>Google — @react-native-google-signin 은 webClientId 로 발급된 id_token 을 준다.
 *       iOS/Android 클라이언트 ID 를 쓰는 구성도 있어 여러 개를 받는다</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "purut.oauth")
public record OAuthProviderProperties(Kakao kakao, Google google, Apple apple) {

    public OAuthProviderProperties {
        kakao = kakao != null ? kakao : new Kakao(null);
        google = google != null ? google : new Google(null, null, null);
        apple = apple != null ? apple : new Apple(null, null, null);
    }

    public record Kakao(String userInfoUri) {
        public Kakao {
            userInfoUri = userInfoUri != null ? userInfoUri : "https://kapi.kakao.com/v2/user/me";
        }
    }

    public record Google(String issuer, String jwksUri, List<String> audiences) {
        public Google {
            issuer = issuer != null ? issuer : "https://accounts.google.com";
            jwksUri = jwksUri != null ? jwksUri : "https://www.googleapis.com/oauth2/v3/certs";
            audiences = audiences != null ? audiences : List.of();
        }
    }

    public record Apple(String issuer, String jwksUri, List<String> audiences) {
        public Apple {
            issuer = issuer != null ? issuer : "https://appleid.apple.com";
            jwksUri = jwksUri != null ? jwksUri : "https://appleid.apple.com/auth/keys";
            audiences = audiences != null ? audiences : List.of();
        }
    }
}
