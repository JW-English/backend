package com.purut.domain.vocabulary;

import java.util.UUID;

/** DAY 별 단어 수. 목록 화면에서 DAY 마다 세면 N+1 이라 한 번에 집계한다. */
public record DayWordCount(UUID dayId, long count) {
}
