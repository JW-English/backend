package com.purut.api.homework.dto;

import com.purut.domain.common.Subject;
import com.purut.domain.homework.Assignment;
import com.purut.domain.homework.HomeworkSubmission;
import com.purut.domain.homework.SubmissionComment;
import com.purut.domain.homework.SubmissionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** 숙제 요청·응답 DTO. Entity 를 그대로 직렬화하지 않는다. */
public final class HomeworkDtos {

    private HomeworkDtos() {
    }

    /** 캘린더 셀 색상용 상태. 미제출·기한초과는 제출물이 없으므로 서버가 계산해 내려준다. */
    public enum CalendarStatus {
        NOT_SUBMITTED,
        SUBMITTED,
        REVIEWED,
        RESUBMIT_REQUIRED,
        OVERDUE
    }

    public record AssignmentListItem(
            UUID id,
            String title,
            Subject subject,
            LocalDate assignedDate,
            LocalDate dueDate,
            CalendarStatus status
    ) {
        public static AssignmentListItem of(Assignment assignment, HomeworkSubmission submission,
                                            LocalDate today) {
            return new AssignmentListItem(
                    assignment.getId(),
                    assignment.getTitle(),
                    assignment.getSubject(),
                    assignment.getAssignedDate(),
                    assignment.getDueDate(),
                    calendarStatus(assignment, submission, today));
        }
    }

    public record AssignmentDetail(
            UUID id,
            String title,
            String description,
            Subject subject,
            LocalDate assignedDate,
            LocalDate dueDate,
            boolean closed,
            CalendarStatus status,
            SubmissionDetail submission
    ) {
        public static AssignmentDetail of(Assignment assignment, HomeworkSubmission submission,
                                          List<SubmissionComment> comments,
                                          Function<String, String> urlResolver,
                                          LocalDate today) {
            return new AssignmentDetail(
                    assignment.getId(),
                    assignment.getTitle(),
                    assignment.getDescription(),
                    assignment.getSubject(),
                    assignment.getAssignedDate(),
                    assignment.getDueDate(),
                    assignment.isClosed(today),
                    calendarStatus(assignment, submission, today),
                    submission == null ? null : SubmissionDetail.of(submission, comments, urlResolver));
        }
    }

    public record SubmissionDetail(
            UUID id,
            SubmissionStatus status,
            Instant submittedAt,
            List<ImageItem> images,
            List<CommentItem> comments
    ) {
        public static SubmissionDetail of(HomeworkSubmission submission,
                                          List<SubmissionComment> comments,
                                          Function<String, String> urlResolver) {
            return new SubmissionDetail(
                    submission.getId(),
                    submission.getStatus(),
                    submission.getSubmittedAt(),
                    submission.getImages().stream()
                            .map(image -> new ImageItem(
                                    image.getId(),
                                    image.getStorageKey(),
                                    urlResolver.apply(image.getStorageKey()),
                                    image.getSortOrder(),
                                    image.getWidth(),
                                    image.getHeight()))
                            .toList(),
                    comments.stream()
                            .map(comment -> new CommentItem(
                                    comment.getId(),
                                    comment.getBody(),
                                    comment.getImageKey() == null ? null
                                            : urlResolver.apply(comment.getImageKey()),
                                    comment.getAuthor() != null && comment.getAuthor().getRole().isStaff(),
                                    comment.getCreatedAt()))
                            .toList());
        }
    }

    /**
     * 표시용 URL 은 만료되므로 조회 시점에 만든다.
     *
     * storageKey 도 함께 내려주는 이유는 재제출(교체) 때문이다 — 남길 사진을 다시 지정하려면
     * 클라이언트가 키를 알아야 한다. URL 에서 키를 되짚는 방식은 스토리지가 R2 로 바뀌면
     * (버킷이 경로에 없다) 깨진다. 키는 추측 불가능한 UUID 이고 버킷은 비공개라,
     * 소유자에게 자기 사진의 키를 알려주는 것 자체는 위험하지 않다.
     */
    public record ImageItem(UUID id, String storageKey, String url, int sortOrder,
                            Integer width, Integer height) {
    }

    public record CommentItem(UUID id, String body, String imageUrl, boolean fromTeacher,
                              Instant createdAt) {
    }

    public record SubmitRequest(
            @NotNull @Size(min = 1, max = 10, message = "사진은 1~10장까지 첨부할 수 있습니다")
            List<@Valid ImageRef> images
    ) {
    }

    public record ImageRef(
            @NotBlank String storageKey,
            Integer width,
            Integer height
    ) {
    }

    private static CalendarStatus calendarStatus(Assignment assignment, HomeworkSubmission submission,
                                                 LocalDate today) {
        if (submission == null) {
            return assignment.isClosed(today) ? CalendarStatus.OVERDUE : CalendarStatus.NOT_SUBMITTED;
        }
        return switch (submission.getStatus()) {
            case SUBMITTED -> CalendarStatus.SUBMITTED;
            case REVIEWED -> CalendarStatus.REVIEWED;
            case RESUBMIT_REQUIRED -> CalendarStatus.RESUBMIT_REQUIRED;
        };
    }
}
