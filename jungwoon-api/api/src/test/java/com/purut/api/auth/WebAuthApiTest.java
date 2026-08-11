package com.purut.api.auth;

import com.purut.api.IntegrationTestSupport;
import com.purut.domain.user.Role;
import com.purut.domain.user.User;
import com.purut.domain.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 웹 인증 — Refresh 를 httpOnly 쿠키로 주고받는 경로. */
class WebAuthApiTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    private String createUser(Role role) throws Exception {
        String email = "web-%s@test.com".formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234","name":"박선생"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        if (role != Role.STUDENT) {
            User user = userRepository.findByEmail(email).orElseThrow();
            user.changeRole(role);
            userRepository.saveAndFlush(user);
        }
        return email;
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/web/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andReturn();
    }

    @Test
    @DisplayName("로그인하면 Refresh 는 httpOnly 쿠키로만 오고 본문에는 없다")
    void loginSetsHttpOnlyCookie() throws Exception {
        String email = createUser(Role.TEACHER);

        MvcResult result = login(email);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        Cookie cookie = result.getResponse().getCookie(RefreshCookies.NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getValue()).isNotBlank();

        // 본문에 Refresh Token 이 섞여 나가면 쿠키를 쓰는 의미가 없다
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("accessToken");
        assertThat(body).doesNotContain("refreshToken");

        // SameSite 는 Cookie 객체에 없으므로 헤더에서 확인한다
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("SameSite=Strict");
    }

    @Test
    @DisplayName("쿠키로 재발급하면 새 Access Token 과 새 쿠키를 받는다")
    void refreshWithCookie() throws Exception {
        String email = createUser(Role.TEACHER);
        Cookie cookie = login(email).getResponse().getCookie(RefreshCookies.NAME);

        MvcResult refreshed = mockMvc.perform(post("/api/auth/web/refresh").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        Cookie rotated = refreshed.getResponse().getCookie(RefreshCookies.NAME);
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(cookie.getValue());
    }

    @Test
    @DisplayName("쿠키가 없으면 재발급은 401")
    void refreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/auth/web/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("학생 계정은 관리자 웹에 로그인할 수 없다")
    void studentCannotLogin() throws Exception {
        String email = createUser(Role.STUDENT);

        mockMvc.perform(post("/api/auth/web/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("로그아웃하면 쿠키가 만료되고 그 토큰은 재사용할 수 없다")
    void logout() throws Exception {
        String email = createUser(Role.TEACHER);
        Cookie cookie = login(email).getResponse().getCookie(RefreshCookies.NAME);

        MvcResult loggedOut = mockMvc.perform(post("/api/auth/web/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(loggedOut.getResponse().getCookie(RefreshCookies.NAME).getMaxAge()).isZero();

        mockMvc.perform(post("/api/auth/web/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }
}
