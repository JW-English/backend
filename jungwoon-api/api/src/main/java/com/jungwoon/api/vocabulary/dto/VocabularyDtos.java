package com.jungwoon.api.vocabulary.dto;

import com.jungwoon.domain.vocabulary.QuizAttempt;
import com.jungwoon.domain.vocabulary.Word;
import com.jungwoon.domain.vocabulary.WordDay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class VocabularyDtos {

    private VocabularyDtos() {
    }

    /**
     * DAY 카드.
     * 재응시가 무제한이라 최초 점수와 최고 점수를 함께 준다 —
     * 선생님 평가 기준은 최초 점수고, 학생 동기부여는 최고 점수다.
     */
    public record DayListItem(
            UUID id,
            int dayNo,
            String title,
            LocalDate scheduledDate,
            long wordCount,
            int attemptCount,
            BigDecimal firstScore,
            BigDecimal bestScore,
            /** 제출하지 않고 나간 응시가 있으면 그 id — 앱이 "이어서 풀기"를 띄운다 */
            UUID inProgressAttemptId
    ) {
        public static DayListItem of(WordDay day, long wordCount, List<QuizAttempt> attempts,
                                     UUID inProgressAttemptId) {
            return new DayListItem(
                    day.getId(),
                    day.getDayNo(),
                    day.getTitle(),
                    day.getScheduledDate(),
                    wordCount,
                    attempts.size(),
                    attempts.isEmpty() ? null : attempts.get(0).getScore(),
                    attempts.stream()
                            .map(QuizAttempt::getScore)
                            .filter(java.util.Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(null),
                    inProgressAttemptId);
        }
    }

    /** 단어장 항목. */
    public record WordItem(
            Long id,
            String headword,
            String meaningKo,
            String exampleEn,
            String exampleKo
    ) {
        public static WordItem of(Word word) {
            return new WordItem(word.getId(), word.getHeadword(), word.getMeaningKo(),
                    word.getExampleEn(), word.getExampleKo());
        }
    }

    public record DayDetail(
            UUID id,
            int dayNo,
            String title,
            LocalDate scheduledDate,
            List<WordItem> words,
            /** 이어 풀 응시가 있으면 그 id */
            UUID inProgressAttemptId
    ) {
    }
}
