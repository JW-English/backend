package com.purut.api.auth;

import com.purut.api.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private String uniqueEmail() {
        return "student-" + UUID.randomUUID() + "@test.com";
    }

    private JsonNode signUp(String email) throws Exception {
        String body = """
                {"email":"%s","password":"password1234","name":"김학생"}
                """.formatted(email);

        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response);
    }

    @Test
    @DisplayName("가입 → 발급된 토큰으로 /api/me 조회까지 관통한다")
    void signUpThenMe() throws Exception {
        String email = uniqueEmail();
        JsonNode tokens = signUp(email);

        assertThat(tokens.get("onboardingRequired").asBoolean()).isTrue();

        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.onboarded").value(false));
    }

    @Test
    @DisplayName("토큰 없이 /api/me 를 호출하면 401 이다")
    void meWithoutToken() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("위조된 토큰은 401 이고 사유를 code 로 알려준다")
    void meWithForgedToken() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("학생은 관리자 경로에 접근할 수 없다")
    void studentCannotAccessAdmin() throws Exception {
        JsonNode tokens = signUp(uniqueEmail());

        mockMvc.perform(get("/api/admin/anything")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 이고, 가입 여부를 노출하지 않는다")
    void loginWithWrongPassword() throws Exception {
        String email = uniqueEmail();
        signUp(email);

        String wrong = """
                {"email":"%s","password":"wrong-password"}
                """.formatted(email);
        String unknown = """
                {"email":"nobody-%s@test.com","password":"password1234"}
                """.formatted(UUID.randomUUID());

        String wrongMessage = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownMessage = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(unknown))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // 계정 열거 방지: 두 응답이 구분되면 안 된다
        assertThat(objectMapper.readTree(wrongMessage).get("detail"))
                .isEqualTo(objectMapper.readTree(unknownMessage).get("detail"));
    }

    @Test
    @DisplayName("같은 이메일로 두 번 가입할 수 없다")
    void duplicateEmail() throws Exception {
        String email = uniqueEmail();
        signUp(email);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234","name":"다른학생"}
                                """.formatted(email)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("짧은 비밀번호는 400 이고 필드별 사유를 준다")
    void weakPassword() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"short","name":"김학생"}
                                """.formatted(uniqueEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
    }
}
