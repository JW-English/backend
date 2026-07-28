package com.jungwoon.domain.listening;

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
import java.util.Objects;
import java.util.UUID;

/** 문항별 학습 진도. 이어듣기와 완주율 집계에 쓴다. */
@Entity
@Getter
@Table(name = "listening_progress")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningProgress {

    @EmbeddedId
    private ListeningProgressId id;

    @Column(name = "last_position_ms", nullable = false)
    private int lastPositionMs;

    @Column(name = "play_count", nullable = false)
    private int playCount;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ListeningProgress(UUID studentId, UUID itemId) {
        this.id = new ListeningProgressId(studentId, itemId);
        this.lastPositionMs = 0;
        this.playCount = 0;
        this.updatedAt = Instant.now();
    }

    public void record(int lastPositionMs, boolean completed) {
        this.lastPositionMs = lastPositionMs;
        this.updatedAt = Instant.now();
        if (completed && completedAt == null) {
            this.completedAt = Instant.now();
        }
    }

    public void increasePlayCount() {
        this.playCount++;
        this.updatedAt = Instant.now();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class ListeningProgressId implements Serializable {

        @Column(name = "student_id")
        private UUID studentId;

        @Column(name = "item_id")
        private UUID itemId;

        public ListeningProgressId(UUID studentId, UUID itemId) {
            this.studentId = studentId;
            this.itemId = itemId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ListeningProgressId other)) return false;
            return Objects.equals(studentId, other.studentId) && Objects.equals(itemId, other.itemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(studentId, itemId);
        }
    }
}
