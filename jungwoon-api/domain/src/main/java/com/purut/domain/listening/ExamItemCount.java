package com.purut.domain.listening;

import java.util.UUID;

/** 시험별 문항 수. 목록 화면에서 시험마다 세면 N+1 이 된다. */
public record ExamItemCount(UUID examId, long count) {
}
