package com.jungwoon.api.auth;

import com.jungwoon.api.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Refresh Token 회전과 재사용 감지 — 탈취 대응의 핵심이라 별도로 검증한다. */
class RefreshTokenRotationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private JsonNode signUp() throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"rt-%s@test.com","password":"password1234","name":"김학생"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("리프레시하면 새 토큰이 나오고 기존 토큰은 무효가 된다")
    void rotation() throws Exception {
        String oldToken = signUp().get("refreshToken").asText();

        JsonNode rotated = objectMapper.readTree(refresh(oldToken));
        String newToken = rotated.get("refreshToken").asText();

        assertThat(newToken).isNotEqualTo(oldToken);

        // 회전된 뒤에는 예전 토큰이 통하지 않아야 한다
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    @DisplayName("재사용이 감지되면 그 세션의 최신 토큰까지 전부 폐기된다")
    void reuseRevokesWholeFamily() throws Exception {
        String first = signUp().get("refreshToken").asText();
        String second = objectMapper.readTree(refresh(first)).get("refreshToken").asText();

        // 탈취된 예전 토큰이 사용됨 → 패밀리 전체 폐기
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(first)))
                .andExpect(status().isUnauthorized());

        // 정상 사용자가 들고 있던 최신 토큰도 더 이상 통하지 않는다 (재로그인 유도)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(second)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("로그아웃하면 해당 세션의 토큰이 폐기된다")
    void logout() throws Exception {
        String refreshToken = signUp().get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }
}
