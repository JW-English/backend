package com.jungwoon.domain.vocabulary;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** DAY 에 담긴 단어. 복합키(day_id, word_id). */
@Entity
@Getter
@Table(name = "word_day_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WordDayItem {

    @EmbeddedId
    private WordDayItemId id;

    @MapsId("dayId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_id", nullable = false)
    private WordDay day;

    @MapsId("wordId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public WordDayItem(WordDay day, Word word, int sortOrder) {
        this.id = new WordDayItemId(day.getId(), word.getId());
        this.day = day;
        this.word = word;
        this.sortOrder = sortOrder;
    }

    @Embeddable
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class WordDayItemId implements Serializable {

        @Column(name = "day_id")
        private UUID dayId;

        @Column(name = "word_id")
        private Long wordId;

        public WordDayItemId(UUID dayId, Long wordId) {
            this.dayId = dayId;
            this.wordId = wordId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WordDayItemId other)) return false;
            return Objects.equals(dayId, other.dayId) && Objects.equals(wordId, other.wordId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dayId, wordId);
        }
    }
}
