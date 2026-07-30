package com.jungwoon.api.vocabulary.dto;

import com.jungwoon.domain.vocabulary.QuestionType;
import com.jungwoon.domain.vocabulary.QuizAnswer;
import com.jungwoon.domain.vocabulary.QuizAttempt;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuizDtos {

    private QuizDtos() {
    }

    public record StartRequest(
            @NotNull UUID dayId,
            /** 비우면 DAY 의 단어 전부. 한 DAY 가 최대 61단어다 */
            @Min(1) @Max(100) Integer questionCount,
            QuestionType questionType
    ) {
    }

    /**
     * 진행 중 문항.
     * <b>correctIndex 가 없다는 점이 핵심이다.</b> 정답을 내려보내면 앱 조작으로 만점이 나온다.
     */
    public record QuestionItem(
            Long wordId,
            int sortOrder,
            QuestionType questionType,
            /** EN_TO_KO 면 영단어, KO_TO_EN 이면 뜻 */
            String prompt,
            List<String> choices,
            Integer selectedIndex
    ) {
        public static QuestionItem of(QuizAnswer answer) {
            String prompt = answer.getQuestionType() == QuestionType.EN_TO_KO
                    ? answer.getWord().getHeadword()
                    : answer.getWord().getMeaningKo();

            return new QuestionItem(answer.getWord().getId(), answer.getSortOrder(),
                    answer.getQuestionType(), prompt, answer.getChoices(), answer.getSelectedIndex());
        }
    }

    public record AttemptResponse(
            UUID attemptId,
            UUID dayId,
            int totalCount,
            List<QuestionItem> questions
    ) {
        public static AttemptResponse of(QuizAttempt attempt) {
            return new AttemptResponse(attempt.getId(), attempt.getDay().getId(),
                    attempt.getAnswers().size(),
                    attempt.getAnswers().stream().map(QuestionItem::of).toList());
        }
    }

    public record AnswerRequest(
            @NotNull Long wordId,
            @NotNull @Min(0) @Max(3) Integer selectedIndex
    ) {
    }

    /** 채점이 끝난 뒤에만 정답을 공개한다. */
    public record ReviewItem(
            Long wordId,
            String headword,
            String meaningKo,
            String exampleEn,
            List<String> choices,
            int correctIndex,
            Integer selectedIndex,
            boolean correct
    ) {
        public static ReviewItem of(QuizAnswer answer) {
            return new ReviewItem(
                    answer.getWord().getId(),
                    answer.getWord().getHeadword(),
                    answer.getWord().getMeaningKo(),
                    answer.getWord().getExampleEn(),
                    answer.getChoices(),
                    answer.getCorrectIndex(),
                    answer.getSelectedIndex(),
                    Boolean.TRUE.equals(answer.getIsCorrect()));
        }
    }

    public record ResultResponse(
            UUID attemptId,
            int totalCount,
            int correctCount,
            BigDecimal score,
            /** 정답률 90% 이상 */
            boolean passed,
            int passPercent,
            Instant startedAt,
            Instant finishedAt,
            List<ReviewItem> reviews
    ) {
        public static ResultResponse of(QuizAttempt attempt) {
            return new ResultResponse(
                    attempt.getId(),
                    attempt.getTotalCount(),
                    attempt.getCorrectCount(),
                    attempt.getScore(),
                    attempt.isPassed(),
                    QuizAttempt.PASS_PERCENT,
                    attempt.getStartedAt(),
                    attempt.getFinishedAt(),
                    attempt.getAnswers().stream().map(ReviewItem::of).toList());
        }
    }

    /** 오답노트 항목. */
    public record WrongNoteItem(
            Long wordId,
            String headword,
            String meaningKo,
            int wrongCount,
            int streakCount,
            Instant lastWrongAt
    ) {
    }

    /** 마이페이지 단어시험 이력 한 줄 */
    public record AttemptHistoryItem(
            UUID id,
            UUID dayId,
            int dayNo,
            String dayTitle,
            int totalCount,
            int correctCount,
            /** 0~100 */
            double score,
            boolean passed,
            Instant finishedAt
    ) {
    }
}
