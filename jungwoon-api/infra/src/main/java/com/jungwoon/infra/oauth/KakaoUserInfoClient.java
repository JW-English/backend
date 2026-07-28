package com.jungwoon.infra.oauth;

import com.jungwoon.domain.user.Provider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 카카오 프로필 조회. https://kapi.kakao.com/v2/user/me
 *
 * 우리 서버의 Client Secret 은 필요 없다 — 클라이언트가 받은 access_token 을
 * 카카오에 되물어 유효성과 소유자를 확인하는 방식이기 때문이다.
 */
@Component
public class KakaoUserInfoClient implements OAuth2UserInfoClient {

    private final RestClient restClient;

    public KakaoUserInfoClient(@Value("${jungwoon.oauth.kakao.user-info-uri:https://kapi.kakao.com/v2/user/me}") String userInfoUri) {
        this.restClient = RestClient.builder().baseUrl(userInfoUri).build();
    }

    @Override
    public Provider provider() {
        return Provider.KAKAO;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuth2UserInfo fetch(String accessToken) {
        Map<String, Object> body = restClient.get()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);

        if (body == null || body.get("id") == null) {
            throw new OAuth2ProfileException("카카오 프로필을 가져오지 못했습니다.");
        }

        String providerId = String.valueOf(body.get("id"));
        Map<String, Object> account = (Map<String, Object>) body.get("kakao_account");
        Map<String, Object> profile = account != null ? (Map<String, Object>) account.get("profile") : null;

        // 이메일은 동의 항목이라 없을 수 있다. 없으면 소셜 전용 계정으로 만든다.
        String email = account != null ? (String) account.get("email") : null;
        String nickname = profile != null ? (String) profile.get("nickname") : null;

        return new OAuth2UserInfo(providerId, email, nickname);
    }
}
