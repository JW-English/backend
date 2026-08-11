package com.purut.domain.user;

/** 소셜 로그인 제공자. 추가 시 OAuth2UserInfoClient 구현체 1개만 작성하면 된다. */
public enum Provider {
    GOOGLE,
    KAKAO,
    NAVER,
    APPLE
}
