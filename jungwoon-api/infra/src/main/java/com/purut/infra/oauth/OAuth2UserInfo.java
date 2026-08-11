package com.purut.infra.oauth;

/**
 * 소셜 제공자에게서 <b>서버가 직접</b> 조회한 프로필.
 * 클라이언트가 보낸 이메일·이름은 신뢰하지 않는다 (위조 가능).
 */
public record OAuth2UserInfo(String providerId, String email, String nickname) {
}
