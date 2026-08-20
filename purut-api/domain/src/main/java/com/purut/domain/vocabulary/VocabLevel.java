package com.purut.domain.vocabulary;

/**
 * 어휘 레벨.
 *
 * 이름은 학년을 따르지만 {@code users.grade}(학교 학년)와는 별개다. 1:1 과외에서는
 * 고3 학생에게 고1 단어장을 지정해야 하는 경우가 흔해, 학생마다
 * {@code users.vocab_level} 을 따로 둔다.
 */
public enum VocabLevel {
    GRADE_1("고1"),
    GRADE_2("고2"),
    GRADE_3("고3");

    private final String label;

    VocabLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
