package com.jungwoon.domain.listening;

import java.util.UUID;

/** 시험별 학습 완료 문항 수 (학생 1명 기준). */
public record ExamCompletedCount(UUID examId, long count) {
}
