package com.purut.api.admin.dto;

import com.purut.api.homework.dto.HomeworkDtos.ImageItem;
import com.purut.domain.common.Subject;
import com.purut.domain.homework.Assignment;
import com.purut.domain.homework.HomeworkSubmission;
import com.purut.domain.homework.SubmissionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * 선생님 전용 DTO.
 *
 * 학생용과 물리적으로 분리한다 — 한 DTO 를 공유하면 언젠가 학생 응답에
 * 실명·전체 목록 같은 것이 새어 나간다.
 */
public final class AdminHomeworkDtos {

    private AdminHomeworkDtos() {
    }

    public record CreateAssignmentRequest(
            /** null 이면 전체 학생 대상(공지형) */
            UUID studentId,
            Subject subject,
            @NotBlank @Size(max = 200) String title,
            String description,
            LocalDate assignedDate,
            @NotNull LocalDate dueDate
    ) {
    }

    public record UpdateAssignmentRequest(
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotNull LocalDate dueDate
    ) {
    }

    public record AdminAssignmentItem(
            UUID id,
            String title,
            Subject subject,
            LocalDate assignedDate,
            LocalDate dueDate,
            UUID studentId,
            String studentName
    ) {
        public static AdminAssignmentItem of(Assignment assignment) {
            return new AdminAssignmentItem(
                    assignment.getId(),
                    assignment.getTitle(),
                    assignment.getSubject(),
                    assignment.getAssignedDate(),
                    assignment.getDueDate(),
                    assignment.getStudent() == null ? null : assignment.getStudent().getId(),
                    assignment.getStudent() == null ? "전체" : assignment.getStudent().getName());
        }
    }

    /** 선생님은 학생 실명을 본다 (학생 화면에서는 마스킹된다). */
    public record AdminSubmissionItem(
            UUID id,
            UUID assignmentId,
            String assignmentTitle,
            UUID studentId,
            String studentName,
            SubmissionStatus status,
            Instant submittedAt,
            int imageCount
    ) {
        public static AdminSubmissionItem of(HomeworkSubmission submission) {
            return new AdminSubmissionItem(
                    submission.getId(),
                    submission.getAssignment().getId(),
                    submission.getAssignment().getTitle(),
                    submission.getStudent().getId(),
                    submission.getStudent().getName(),
                    submission.getStatus(),
                    submission.getSubmittedAt(),
                    submission.getImages().size());
        }
    }

    public record AdminSubmissionDetail(
            UUID id,
            UUID assignmentId,
            String assignmentTitle,
            String studentName,
            SubmissionStatus status,
            Instant submittedAt,
            List<ImageItem> images
    ) {
        public static AdminSubmissionDetail of(HomeworkSubmission submission,
                                               Function<String, String> urlResolver) {
            return new AdminSubmissionDetail(
                    submission.getId(),
                    submission.getAssignment().getId(),
                    submission.getAssignment().getTitle(),
                    submission.getStudent().getName(),
                    submission.getStatus(),
                    submission.getSubmittedAt(),
                    submission.getImages().stream()
                            .map(image -> new ImageItem(image.getId(),
                                    image.getStorageKey(),
                                    urlResolver.apply(image.getStorageKey()),
                                    image.getSortOrder(), image.getWidth(), image.getHeight()))
                            .toList());
        }
    }

    public record CommentRequest(
            String body,
            /** 첨삭 이미지 (presign 으로 올린 키) */
            String imageKey,
            /** true 면 학생에게 다시 제출을 요청한다 */
            boolean requestResubmit
    ) {
    }
}
