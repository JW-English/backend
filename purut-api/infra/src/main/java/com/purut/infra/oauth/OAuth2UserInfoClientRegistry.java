package com.purut.infra.oauth;

import com.purut.common.error.BusinessException;
import com.purut.common.error.ErrorCode;
import com.purut.domain.user.Provider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 구현체들을 Map<Provider, Client> 로 모아둔다. 제공자 추가 = 클래스 1개 추가. */
@Component
public class OAuth2UserInfoClientRegistry {

    private final Map<Provider, OAuth2UserInfoClient> clients;

    public OAuth2UserInfoClientRegistry(List<OAuth2UserInfoClient> clients) {
        this.clients = clients.stream()
                .collect(Collectors.toMap(OAuth2UserInfoClient::provider, Function.identity()));
    }

    public OAuth2UserInfoClient get(Provider provider) {
        OAuth2UserInfoClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 로그인 방식입니다: " + provider);
        }
        return client;
    }
}
