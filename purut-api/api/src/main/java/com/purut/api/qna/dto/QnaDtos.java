package com.purut.api.qna.dto;

import com.purut.domain.qna.QnaNotice;
import com.purut.domain.qna.Question;
import com.purut.domain.qna.QuestionAttachment;
import com.purut.domain.qna.QuestionCategory;
import com.purut.domain.qna.QuestionMessage;
import com.purut.domain.qna.QuestionMessageRole;
import com.purut.domain.qna.QuestionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QnaDtos {

    private QnaDtos() {
    }

    public record CursorPage<T>(List<T> items, String nextCursor) {
    }

    public record QuestionListItem(
            UUID id,
            QuestionCategory category,
            QuestionStatus status,
            String title,
            String authorName,
            boolean mine,
            boolean publicVisible,
            Instant createdAt,
            int answerCount
    ) {
        public static QuestionListItem of(Question question, UUID meId, int answerCount) {
            return new QuestionListItem(
                    question.getId(),
                    question.getCategory(),
                    question.getStatus(),
                    question.getTitle(),
                    maskName(question.getAuthor().getName()),
                    question.isMine(meId),
                    question.isPublicVisible(),
                    question.getCreatedAt(),
                    answerCount
            );
        }
    }

    public record QuestionDetail(
            UUID id,
            QuestionCategory category,
            QuestionStatus status,
            String title,
            String body,
            String authorName,
            boolean mine,
            boolean publicVisible,
            Instant createdAt,
            Instant answeredAt,
            ReferenceDto reference,
            List<AttachmentDto> attachments,
            List<MessageDto> messages
    ) {
        public static QuestionDetail of(Question question, UUID meId, List<QuestionAttachment> attachments,
                                        List<QuestionMessage> messages,
                                        Map<UUID, List<QuestionAttachment>> messageAttachments) {
            return new QuestionDetail(
                    question.getId(),
                    question.getCategory(),
                    question.getStatus(),
                    question.getTitle(),
                    question.getBody(),
                    maskName(question.getAuthor().getName()),
                    question.isMine(meId),
                    question.isPublicVisible(),
                    question.getCreatedAt(),
                    question.getAnsweredAt(),
                    ReferenceDto.of(question),
                    attachments.stream().map(AttachmentDto::of).toList(),
                    messages.stream()
                            .map(m -> MessageDto.of(m, messageAttachments.getOrDefault(m.getId(), List.of()), true))
                            .toList()
            );
        }
    }

    public record MessageDto(
            UUID id,
            QuestionMessageRole role,
            String body,
            String authorName,
            Instant createdAt,
            List<AttachmentDto> attachments
    ) {
        public static MessageDto of(QuestionMessage message, List<QuestionAttachment> attachments, boolean masked) {
            String name = masked ? maskName(message.getAuthor().getName()) : message.getAuthor().getName();
            return new MessageDto(message.getId(), message.getRole(), message.getBody(), name,
                    message.getCreatedAt(), attachments.stream().map(AttachmentDto::of).toList());
        }
    }

    public record AttachmentDto(UUID id, String storageKey, String mimeType, int byteSize,
                                Integer width, Integer height, int sortOrder) {
        public static AttachmentDto of(QuestionAttachment attachment) {
            return new AttachmentDto(attachment.getId(), attachment.getStorageKey(), attachment.getMimeType(),
                    attachment.getByteSize(), attachment.getWidth(), attachment.getHeight(),
                    attachment.getSortOrder());
        }
    }

    public record AttachmentUrl(UUID id, String url, long expiresIn) {
    }

    public record ReferenceDto(
            UUID examId,
            Integer itemNo,
            String examTitle,
            UUID wordDayId,
            Integer wordDayNo,
            String wordDayTitle,
            UUID assignmentId,
            String assignmentTitle,
            String textbook,
            Integer page
    ) {
        public static ReferenceDto of(Question q) {
            return new ReferenceDto(
                    q.getRefExam() == null ? null : q.getRefExam().getId(),
                    q.getRefItemNo(),
                    q.getRefExam() == null ? null : q.getRefExam().getTitle(),
                    q.getRefWordDay() == null ? null : q.getRefWordDay().getId(),
                    q.getRefWordDay() == null ? null : q.getRefWordDay().getDayNo(),
                    q.getRefWordDay() == null ? null : q.getRefWordDay().getTitle(),
                    q.getRefAssignment() == null ? null : q.getRefAssignment().getId(),
                    q.getRefAssignment() == null ? null : q.getRefAssignment().getTitle(),
                    q.getRefTextbook(),
                    q.getRefPage()
            );
        }
    }

    public record CreateQuestionRequest(
            @NotNull QuestionCategory category,
            @NotBlank @Size(min = 2, max = 60) String title,
            @NotBlank @Size(min = 10, max = 2000) String body,
            boolean publicVisible,
            UUID refExamId,
            @Min(1) @Max(99) Integer refItemNo,
            UUID refWordDayId,
            UUID refAssignmentId,
            @Size(max = 100) String refTextbook,
            @Min(1) @Max(2000) Integer refPage,
            @Size(max = 5) List<@Valid AttachmentRequest> attachments
    ) {
        public List<AttachmentRequest> attachmentsOrEmpty() {
            return attachments == null ? List.of() : attachments;
        }
    }

    public record UpdateQuestionRequest(
            @NotBlank @Size(min = 2, max = 60) String title,
            @NotBlank @Size(min = 10, max = 2000) String body,
            boolean publicVisible
    ) {
    }

    public record CreateMessageRequest(
            @NotBlank @Size(min = 2, max = 2000) String body,
            @Size(max = 5) List<@Valid AttachmentRequest> attachments
    ) {
        public List<AttachmentRequest> attachmentsOrEmpty() {
            return attachments == null ? List.of() : attachments;
        }
    }

    public record AttachmentRequest(
            @NotBlank String storageKey,
            @NotBlank String mimeType,
            @Min(1) @Max(10_485_760) int byteSize,
            Integer width,
            Integer height
    ) {
    }

    public record QnaNoticeItem(UUID id, String title, String body, Instant createdAt) {
        public static QnaNoticeItem of(QnaNotice notice) {
            return new QnaNoticeItem(notice.getId(), notice.getTitle(), notice.getBody(), notice.getCreatedAt());
        }
    }

    public record VisibilityRequest(boolean publicVisible) {
    }

    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "익명";
        }
        return name.strip().substring(0, 1) + "**";
    }
}
