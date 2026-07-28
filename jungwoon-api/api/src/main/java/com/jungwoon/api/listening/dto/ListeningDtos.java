package com.jungwoon.api.listening.dto;

import com.jungwoon.domain.listening.Exam;
import com.jungwoon.domain.listening.ExamType;
import com.jungwoon.domain.listening.ListeningItem;
import com.jungwoon.domain.listening.ListeningSentence;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public final class ListeningDtos {

    private ListeningDtos() {
    }

    public record ExamListItem(
            UUID id,
            int year,
            ExamType examType,
            String examTypeLabel,
            int grade,
            String title,
            int itemCount,
            int completedCount
    ) {
        public static ExamListItem of(Exam exam, int itemCount, int completedCount) {
            return new ExamListItem(exam.getId(), exam.getYear(), exam.getExamType(),
                    exam.getExamType().label(), exam.getGrade(), exam.getTitle(),
                    itemCount, completedCount);
        }
    }

    public record ItemListItem(
            UUID id,
            int itemNo,
            String itemType,
            String questionText,
            Integer durationMs,
            boolean completed,
            int lastPositionMs
    ) {
        public static ItemListItem of(ListeningItem item, Integer lastPositionMs, boolean completed) {
            return new ItemListItem(item.getId(), item.getItemNo(), item.getItemType(),
                    item.getQuestionText(), item.getDurationMs(), completed,
                    lastPositionMs == null ? 0 : lastPositionMs);
        }
    }

    /**
     * 문장 1개. start/end 로 앱이 단일 음원 안에서 seek 한다.
     * 문장을 누르면 startMs 로 이동해 재생하는 구조다.
     */
    public record SentenceItem(
            long id,
            int seq,
            String speaker,
            String textEn,
            String textKo,
            int startMs,
            int endMs
    ) {
        public static SentenceItem of(ListeningSentence sentence) {
            return new SentenceItem(sentence.getId(), sentence.getSeq(), sentence.getSpeaker(),
                    sentence.getTextEn(), sentence.getTextKo(),
                    sentence.getStartMs(), sentence.getEndMs());
        }
    }

    public record ItemDetail(
            UUID id,
            int itemNo,
            String itemType,
            String questionText,
            /** 만료형 재생 URL. 키가 아니라 URL 로 내려준다 */
            String audioUrl,
            Integer durationMs,
            int lastPositionMs,
            List<SentenceItem> sentences
    ) {
    }

    public record ProgressRequest(
            @NotNull @Min(0) Integer lastPositionMs,
            boolean completed
    ) {
    }
}
