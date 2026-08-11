package com.purut.api.health;

import com.purut.api.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthApiTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("헬스체크는 인증 없이 호출된다")
    void ping() throws Exception {
        mockMvc.perform(get("/api/public/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("purut-api"));
    }

    @Test
    @DisplayName("actuator health 는 공개 엔드포인트다")
    void actuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("인증이 필요한 경로는 401/403 으로 막힌다")
    void protectedEndpointIsBlocked() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().is4xxClientError());
    }
}
