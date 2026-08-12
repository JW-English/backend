package com.purut.api.support.query;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청마다 쿼리 수를 로그로 남긴다.
 *
 *   GET /api/listening/exams → 3 queries, 4ms
 *   GET /api/homework/assignments → 27 queries, 41ms  ⚠️ N+1 의심
 *
 * 개발 프로파일에서만 동작한다. 운영에서 요청마다 로그를 남길 이유가 없고,
 * p6spy 자체가 운영 jar 에 들어가지 않는다.
 */
@Component
@Profile("local")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QueryCountFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(QueryCountFilter.class);

    /** 이보다 많으면 N+1 을 의심할 만하다. 화면 하나가 쓰는 쿼리로는 과하다. */
    private static final int SUSPICIOUS_THRESHOLD = 10;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        QueryCounter.start();
        try {
            chain.doFilter(request, response);
        } finally {
            QueryCounter.Stat stat = QueryCounter.finish();
            if (stat != null && stat.count() > 0) {
                String suffix = stat.count() >= SUSPICIOUS_THRESHOLD ? "  ⚠️ N+1 의심" : "";
                log.info("{} {} → {} queries, {}ms{}",
                        request.getMethod(), request.getRequestURI(),
                        stat.count(), stat.elapsedMillis(), suffix);
            }
        }
    }

    /** 정적 리소스·문서 요청까지 찍으면 로그가 지저분해진다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
