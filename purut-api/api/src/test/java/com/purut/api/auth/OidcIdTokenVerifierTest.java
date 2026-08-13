package com.purut.api.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.purut.infra.oauth.OAuth2ProfileException;
import com.purut.infra.oauth.OidcIdTokenVerifier;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * id_token 검증.
 *
 * 서명·만료만 보고 aud 를 안 보면 <b>다른 앱</b>이 같은 제공자에서 받은 토큰으로도
 * 로그인이 된다. 그 구멍이 막혀 있는지가 이 테스트의 핵심이다.
 *
 * 실제 Apple·Google 을 부를 수 없으므로 JWKS 를 흉내내는 서버를 띄우고
 * 우리가 만든 키로 토큰에 서명한다.
 */
class OidcIdTokenVerifierTest {

    private static final String ISSUER = "https://appleid.apple.com";
    private static final String OUR_APP = "com.purut";

    private static HttpServer jwks;
    private static String jwksUri;
    private static RSAKey key;
    private static OidcIdTokenVerifier verifier;

    @BeforeAll
    static void setUp() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();

        // 실제 Apple·Google 을 부를 수 없으니 공개키만 내주는 서버를 띄운다.
        // 별도 라이브러리 없이 JDK 내장 서버로 충분하다
        String body = new com.nimbusds.jose.jwk.JWKSet(key.toPublicJWK()).toString();
        jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwks.createContext("/keys", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        jwks.start();
        jwksUri = "http://127.0.0.1:" + jwks.getAddress().getPort() + "/keys";

        verifier = new OidcIdTokenVerifier("Apple", jwksUri, ISSUER, List.of(OUR_APP));
    }

    @AfterAll
    static void tearDown() {
        jwks.stop(0);
    }

    private String token(String issuer, String audience, Instant expiresAt) throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .subject("001234.abcdef.5678")
                .issuer(issuer)
                .audience(audience)
                .expirationTime(Date.from(expiresAt))
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .claim("email", "student@example.com")
                .claim("email_verified", "true")
                .build();

        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Test
    @DisplayName("우리 앱에 발급된 정상 토큰은 통과한다")
    void acceptsValidToken() throws Exception {
        var jwt = verifier.verify(token(ISSUER, OUR_APP, Instant.now().plusSeconds(600)));

        assertThat(jwt.getSubject()).isEqualTo("001234.abcdef.5678");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("student@example.com");
    }

    @Test
    @DisplayName("다른 앱에 발급된 토큰은 거부한다 — 서명이 맞아도")
    void rejectsOtherApplication() throws Exception {
        assertThatThrownBy(() -> verifier.verify(token(ISSUER, "com.someone.else", Instant.now().plusSeconds(600))))
                .isInstanceOf(OAuth2ProfileException.class);
    }

    @Test
    @DisplayName("발급자가 다르면 거부한다")
    void rejectsWrongIssuer() throws Exception {
        assertThatThrownBy(() -> verifier.verify(token("https://evil.example.com", OUR_APP, Instant.now().plusSeconds(600))))
                .isInstanceOf(OAuth2ProfileException.class);
    }

    @Test
    @DisplayName("만료된 토큰은 거부한다")
    void rejectsExpired() throws Exception {
        assertThatThrownBy(() -> verifier.verify(token(ISSUER, OUR_APP, Instant.now().minusSeconds(60))))
                .isInstanceOf(OAuth2ProfileException.class);
    }

    @Test
    @DisplayName("서명이 우리가 아는 키가 아니면 거부한다")
    void rejectsForeignSignature() throws Exception {
        RSAKey attacker = new RSAKeyGenerator(2048).keyID("test-key").generate();
        var claims = new JWTClaimsSet.Builder()
                .subject("001234.abcdef.5678").issuer(ISSUER).audience(OUR_APP)
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(attacker));

        assertThatThrownBy(() -> verifier.verify(jwt.serialize()))
                .isInstanceOf(OAuth2ProfileException.class);
    }

    @Test
    @DisplayName("audiences 를 설정하지 않으면 로그인 자체를 막는다")
    void rejectsWhenNotConfigured() throws Exception {
        var unconfigured = new OidcIdTokenVerifier("Apple", jwksUri, ISSUER, List.of());

        assertThatThrownBy(() -> unconfigured.verify(token(ISSUER, OUR_APP, Instant.now().plusSeconds(600))))
                .isInstanceOf(OAuth2ProfileException.class)
                .hasMessageContaining("설정");
    }
}
