package com.purut.api.qna.dto;

import com.purut.domain.qna.Question;
import com.purut.domain.qna.QuestionAttachment;
import com.purut.domain.qna.QuestionCategory;
import com.purut.domain.qna.QuestionMessage;
import com.purut.domain.qna.QuestionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminQnaDtos {

    private AdminQnaDtos() {
    }

    public record AdminQuestionListItem(
            UUID id,
            QuestionCategory category,
            QuestionStatus status,
            String title,
            String authorName,
            boolean publicVisible,
            Instant createdAt,
            int answerCount
    ) {
        public static AdminQuestionListItem of(Question question, int answerCount) {
            return new AdminQuestionListItem(question.getId(), question.getCategory(), question.getStatus(),
                    question.getTitle(), question.getAuthor().getName(), question.isPublicVisible(),
                    question.getCreatedAt(), answerCount);
        }
    }

    public record AdminQuestionDetail(
            UUID id,
            QuestionCategory category,
            QuestionStatus status,
            String title,
            String body,
            String authorName,
            UUID authorId,
            boolean publicVisible,
            Instant createdAt,
            Instant answeredAt,
            QnaDtos.ReferenceDto reference,
            List<QnaDtos.AttachmentDto> attachments,
            List<QnaDtos.MessageDto> messages
    ) {
        public static AdminQuestionDetail of(Question question, List<QuestionAttachment> attachments,
                                             List<QuestionMessage> messages,
                                             Map<UUID, List<QuestionAttachment>> messageAttachments) {
            return new AdminQuestionDetail(
                    question.getId(),
                    question.getCategory(),
                    question.getStatus(),
                    question.getTitle(),
                    question.getBody(),
                    question.getAuthor().getName(),
                    question.getAuthor().getId(),
                    question.isPublicVisible(),
                    question.getCreatedAt(),
                    question.getAnsweredAt(),
                    QnaDtos.ReferenceDto.of(question),
                    attachments.stream().map(QnaDtos.AttachmentDto::of).toList(),
                    messages.stream()
                            .map(m -> QnaDtos.MessageDto.of(m, messageAttachments.getOrDefault(m.getId(), List.of()), false))
                            .toList()
            );
        }
    }
}
