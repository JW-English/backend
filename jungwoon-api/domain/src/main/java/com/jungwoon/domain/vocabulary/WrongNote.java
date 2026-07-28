package com.jungwoon.domain.vocabulary;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * 오답노트 (누적).
 *
 * 간격 반복: 틀리면 3일 뒤, 맞히면 7일 → 14일로 늘리고 3회 연속 정답이면 졸업시킨다.
 * 학습 앱에서 체감 효용이 가장 큰 기능이라 P3 에 같이 넣는다.
 */
@Entity
@Getter
@Table(name = "wrong_notes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WrongNote {

    private static final int MASTER_STREAK = 3;
    private static final int[] REVIEW_INTERVALS_DAYS = {3, 7, 14};

    @EmbeddedId
    private WrongNoteId id;

    @Column(name = "wrong_count", nullable = false)
    private int wrongCount;

    @Column(name = "streak_count", nullable = false)
    private int streakCount;

    @Column(name = "last_wrong_at", nullable = false)
    private Instant lastWrongAt;

    @Column(name = "next_review_at")
    private Instant nextReviewAt;

    @Column(name = "mastered_at")
    private Instant masteredAt;

    public WrongNote(UUID studentId, Long wordId) {
        this.id = new WrongNoteId(studentId, wordId);
        this.wrongCount = 1;
        this.streakCount = 0;
        this.lastWrongAt = Instant.now();
        this.nextReviewAt = Instant.now().plus(REVIEW_INTERVALS_DAYS[0], ChronoUnit.DAYS);
    }

    public void markWrong() {
        this.wrongCount++;
        this.streakCount = 0;
        this.lastWrongAt = Instant.now();
        this.masteredAt = null;
        this.nextReviewAt = Instant.now().plus(REVIEW_INTERVALS_DAYS[0], ChronoUnit.DAYS);
    }

    public void markCorrect() {
        this.streakCount++;
        if (streakCount >= MASTER_STREAK) {
            this.masteredAt = Instant.now();
            this.nextReviewAt = null;
            return;
        }
        int interval = REVIEW_INTERVALS_DAYS[Math.min(streakCount, REVIEW_INTERVALS_DAYS.length - 1)];
        this.nextReviewAt = Instant.now().plus(interval, ChronoUnit.DAYS);
    }

    public boolean isMastered() {
        return masteredAt != null;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class WrongNoteId implements Serializable {

        @Column(name = "student_id")
        private UUID studentId;

        @Column(name = "word_id")
        private Long wordId;

        public WrongNoteId(UUID studentId, Long wordId) {
            this.studentId = studentId;
            this.wordId = wordId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WrongNoteId other)) return false;
            return Objects.equals(studentId, other.studentId) && Objects.equals(wordId, other.wordId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(studentId, wordId);
        }
    }
}
