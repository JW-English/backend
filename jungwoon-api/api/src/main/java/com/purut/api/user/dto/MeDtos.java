package com.purut.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 마이페이지·설정 요청·응답 DTO. Entity 를 그대로 직렬화하지 않는다. */
public final class MeDtos {

    private MeDtos() {
    }

    /**
     * 마이페이지 요약.
     *
     * 화면에 숫자 몇 개를 띄우려고 목록을 통째로 읽지 않는다 — 집계 쿼리 2번으로 끝낸다.
     */
    public record Summary(
            HomeworkSummary homework,
            VocabularySummary vocabulary
    ) {
    }

    public record HomeworkSummary(
            long total,
            long submitted,
            long reviewed,
            /** 아직 안 낸 것. total - submitted */
            long pending
    ) {
    }

    public record VocabularySummary(
            long attemptCount,
            /** 응시가 없으면 null */
            Double averageScore,
            Double bestScore
    ) {
    }

    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다")
            String newPassword
    ) {
    }
}
