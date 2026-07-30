package com.jungwoon.domain.vocabulary;

/**
 * 어휘 레벨. 학교 학년과 별개다.
 *
 * 1:1 과외에서는 고3이 BEGINNER 를 봐야 하는 경우가 흔해, 학년으로 난이도를
 * 대신하면 학생이 자기 레벨을 기본 화면에서 볼 수 없다.
 */
public enum VocabLevel {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced");

    private final String label;

    VocabLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
