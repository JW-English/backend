package com.purut.infra.oauth;

import com.purut.domain.user.Provider;

/**
 * 제공자별 프로필 확인. 구현체를 1개 추가하면 새 제공자가 붙는다.
 *
 * <p>넘기는 자격증명의 종류가 제공자마다 다르다. 어느 쪽이든 <b>서버가 직접 검증</b>하고,
 * 클라이언트가 보낸 이메일·이름은 신원 판단에 쓰지 않는다.
 * <ul>
 *   <li>카카오 — access_token. 제공자 API 에 되물어 소유자를 확인한다</li>
 *   <li>구글·Apple — id_token(JWT). 제공자 공개키(JWKS)로 서명·발급자·수신자를 검증한다</li>
 * </ul>
 */
public interface OAuth2UserInfoClient {

    Provider provider();

    /** @param credential 카카오는 access_token, 구글·Apple 은 id_token */
    OAuth2UserInfo fetch(String credential);
}
