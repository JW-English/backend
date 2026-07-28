package com.jungwoon.api.user;

import com.jungwoon.api.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingApiTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private String signUp() throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"onb-%s@test.com","password":"password1234","name":"김학생"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    @Test
    @DisplayName("온보딩하면 학년이 저장되고 onboarded 가 true 가 된다")
    void completeOnboarding() throws Exception {
        String token = signUp();

        mockMvc.perform(put("/api/me/onboarding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김학생","grade":2,"school":"정운고등학교"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value(2))
                .andExpect(jsonPath("$.onboarded").value(true));

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.school").value("정운고등학교"));
    }

    @Test
    @DisplayName("고1~고3 범위를 벗어난 학년은 거부된다")
    void invalidGrade() throws Exception {
        String token = signUp();

        mockMvc.perform(put("/api/me/onboarding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김학생","grade":5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.grade").exists());
    }

    @Test
    @DisplayName("미인증 상태로는 온보딩할 수 없다")
    void requiresAuth() throws Exception {
        mockMvc.perform(put("/api/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김학생","grade":1}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
