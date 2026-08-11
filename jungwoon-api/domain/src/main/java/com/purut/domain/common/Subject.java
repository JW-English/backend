package com.purut.domain.common;

/**
 * 과목. 국어/수학 확장을 처음부터 전제한다 —
 * 콘텐츠 테이블은 모두 subject 컬럼을 가지므로 스키마 변경 없이 데이터만 추가된다.
 */
public enum Subject {
    ENGLISH,
    KOREAN,
    MATH
}
