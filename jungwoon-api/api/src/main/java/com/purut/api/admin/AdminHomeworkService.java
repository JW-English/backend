package com.purut.api.admin;

import com.purut.api.admin.dto.AdminHomeworkDtos.AdminAssignmentItem;
import com.purut.api.admin.dto.AdminHomeworkDtos.AdminSubmissionDetail;
import com.purut.api.admin.dto.AdminHomeworkDtos.AdminSubmissionItem;
import com.purut.api.admin.dto.AdminHomeworkDtos.CommentRequest;
import com.purut.api.admin.dto.AdminHomeworkDtos.CreateAssignmentRequest;
import com.purut.api.admin.dto.AdminHomeworkDtos.UpdateAssignmentRequest;
import com.purut.api.auth.UserPrincipal;
import com.purut.common.error.BusinessException;
import com.purut.common.error.ErrorCode;
import com.purut.common.error.NotFoundException;
import com.purut.domain.homework.Assignment;
import com.purut.domain.homework.AssignmentRepository;
import com.purut.domain.homework.HomeworkSubmission;
import com.purut.domain.homework.HomeworkSubmissionRepository;
import com.purut.domain.homework.SubmissionComment;
import com.purut.domain.homework.SubmissionCommentRepository;
import com.purut.domain.homework.SubmissionStatus;
import com.purut.domain.user.User;
import com.purut.domain.user.UserRepository;
import com.purut.infra.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 선생님 전용 숙제 서비스. 학생 서비스와 물리적으로 분리한다. */
@Service
public class AdminHomeworkService {

    private final AssignmentRepository assignmentRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final SubmissionCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;

    public AdminHomeworkService(AssignmentRepository assignmentRepository,
                                HomeworkSubmissionRepository submissionRepository,
                                SubmissionCommentRepository commentRepository,
                                UserRepository userRepository,
                                FileStorage fileStorage) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
    }

    @Transactional
    public AdminAssignmentItem create(UserPrincipal me, CreateAssignmentRequest request) {
        User student = request.studentId() == null ? null
                : userRepository.findById(request.studentId())
                        .orElseThrow(() -> new NotFoundException("학생을 찾을 수 없습니다."));

        if (request.assignedDate() != null && request.dueDate().isBefore(request.assignedDate())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "마감일이 부여일보다 빠를 수 없습니다.");
        }

        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .student(student)
                .subject(request.subject())
                .title(request.title())
                .description(request.description())
                .assignedDate(request.assignedDate())
                .dueDate(request.dueDate())
                .createdBy(me.id())
                .build());

        return AdminAssignmentItem.of(assignment);
    }

    @Transactional
    public AdminAssignmentItem update(UUID assignmentId, UpdateAssignmentRequest request) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("숙제를 찾을 수 없습니다."));

        assignment.update(request.title(), request.description(), request.dueDate());
        return AdminAssignmentItem.of(assignment);
    }

    @Transactional(readOnly = true)
    public List<AdminAssignmentItem> listAssignments(UUID studentId) {
        return assignmentRepository.findAllForTeacher(studentId).stream()
                .map(AdminAssignmentItem::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminSubmissionItem> listSubmissions(SubmissionStatus status) {
        return submissionRepository.findAllForTeacher(status).stream()
                .map(AdminSubmissionItem::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminSubmissionDetail getSubmission(UUID submissionId) {
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다."));
        return AdminSubmissionDetail.of(submission, fileStorage::presignDownload);
    }

    /**
     * 첨삭 코멘트. 코멘트를 달면 제출물 상태가 '첨삭완료'로 바뀐다.
     * 다시 제출을 요청하면 학생 화면에서 재제출이 열린다.
     */
    @Transactional
    public void comment(UserPrincipal me, UUID submissionId, CommentRequest request) {
        if ((request.body() == null || request.body().isBlank()) && request.imageKey() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "코멘트 내용이나 이미지가 필요합니다.");
        }
        if (request.imageKey() != null && !fileStorage.exists(request.imageKey())) {
            throw new BusinessException(ErrorCode.FILE_NOT_UPLOADED);
        }

        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다."));

        User author = userRepository.getReferenceById(me.id());
        commentRepository.save(new SubmissionComment(submission, author, request.body(), request.imageKey()));

        if (request.requestResubmit()) {
            submission.requestResubmit();
        } else {
            submission.markReviewed();
        }
    }
}
