package com.jungwoon.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jungwoon.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 미인증 요청은 에러 응답도 ProblemDetail 로 통일한다. */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 필터가 남긴 사유가 있으면 그대로 전달한다 (만료인지 위조인지 앱이 구분해야 한다)
        Object jwtError = request.getAttribute("jwtError");
        ErrorCode code = jwtError != null
                ? ErrorCode.valueOf(jwtError.toString())
                : ErrorCode.UNAUTHORIZED;

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.UNAUTHORIZED, code.defaultMessage());
        problem.setProperty("code", code.name());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
