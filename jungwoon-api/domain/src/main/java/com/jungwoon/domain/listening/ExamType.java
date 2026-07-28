package com.jungwoon.domain.listening;

public enum ExamType {
    SUNEUNG("수능"),
    MOCK_9("9월 모의평가"),
    MOCK_6("6월 모의평가"),
    MOCK_3("3월 학력평가"),
    EDU_OFFICE("교육청 학력평가");

    private final String label;

    ExamType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
