package com.purut.domain.vocabulary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * 출제된 문항 1개.
 *
 * <b>correctIndex 는 절대 응답 DTO 에 넣지 않는다.</b>
 * 정답을 내려보내면 앱 메모리 조작으로 만점이 나온다.
 */
@Entity
@Getter
@Table(name = "quiz_answers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    /** 출제된 보기. 재응시가 아니라 '이 응시를 그대로 재현'하기 위해 저장한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choices", nullable = false)
    private List<String> choices;

    @Column(name = "correct_index", nullable = false)
    private int correctIndex;

    @Column(name = "selected_index")
    private Integer selectedIndex;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "answered_at")
    private Instant answeredAt;

    public QuizAnswer(QuizAttempt attempt, Word word, QuestionType questionType,
                      List<String> choices, int correctIndex, int sortOrder) {
        this.attempt = attempt;
        this.word = word;
        this.questionType = questionType;
        this.choices = choices;
        this.correctIndex = correctIndex;
        this.sortOrder = sortOrder;
    }

    /** 답 선택 — 정오답 판정은 제출 시점에 서버가 한다. */
    public void select(int selectedIndex) {
        this.selectedIndex = selectedIndex;
        this.answeredAt = Instant.now();
    }

    /** 채점. 미응답은 오답으로 처리한다. */
    public boolean grade() {
        this.isCorrect = selectedIndex != null && selectedIndex == correctIndex;
        return this.isCorrect;
    }
}
