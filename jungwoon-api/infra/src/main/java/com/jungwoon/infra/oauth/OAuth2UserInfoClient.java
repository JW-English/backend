package com.jungwoon.infra.oauth;

import com.jungwoon.domain.user.Provider;

/**
 * 제공자별 프로필 조회. 구현체를 1개 추가하면 새 제공자가 붙는다.
 *
 * Apple 은 access_token 이 아니라 id_token(JWT) 검증 방식이라 별도 처리가 필요하다
 * (Apple 공개키(JWKS)로 서명 검증 후 sub 를 providerId 로 사용).
 */
public interface OAuth2UserInfoClient {

    Provider provider();

    OAuth2UserInfo fetch(String accessToken);
}
