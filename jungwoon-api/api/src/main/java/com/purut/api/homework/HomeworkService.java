package com.purut.api.homework;

import com.purut.api.auth.UserPrincipal;
import com.purut.api.homework.dto.HomeworkDtos.AssignmentDetail;
import com.purut.api.homework.dto.HomeworkDtos.AssignmentListItem;
import com.purut.api.homework.dto.HomeworkDtos.ImageRef;
import com.purut.api.homework.dto.HomeworkDtos.SubmitRequest;
import com.purut.common.error.BusinessException;
import com.purut.common.error.ErrorCode;
import com.purut.common.error.NotFoundException;
import com.purut.domain.homework.Assignment;
import com.purut.domain.homework.AssignmentRepository;
import com.purut.domain.homework.HomeworkSubmission;
import com.purut.domain.homework.HomeworkSubmissionRepository;
import com.purut.domain.homework.SubmissionCommentRepository;
import com.purut.domain.homework.SubmissionImage;
import com.purut.domain.user.User;
import com.purut.domain.user.UserRepository;
import com.purut.infra.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 학생용 숙제 서비스. 모든 조회는 본인 것으로 한정된다. */
@Service
public class HomeworkService {

    private final AssignmentRepository assignmentRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final SubmissionCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;

    public HomeworkService(AssignmentRepository assignmentRepository,
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

    /** 캘린더용 목록. 기간 안의 숙제와 내 제출 상태를 함께 내려준다. */
    @Transactional(readOnly = true)
    public List<AssignmentListItem> listAssignments(UserPrincipal me, LocalDate from, LocalDate to) {
        List<Assignment> assignments = assignmentRepository.findVisibleToStudent(me.id(), from, to);
        if (assignments.isEmpty()) {
            return List.of();
        }

        // 숙제마다 제출물을 따로 조회하면 N+1 이 된다 → 한 번에 가져와 맞춘다
        Map<UUID, HomeworkSubmission> submissions = submissionRepository
                .findAllByStudentAndAssignments(me.id(), assignments.stream().map(Assignment::getId).toList())
                .stream()
                .collect(Collectors.toMap(s -> s.getAssignment().getId(), Function.identity()));

        LocalDate today = LocalDate.now();
        return assignments.stream()
                .map(a -> AssignmentListItem.of(a, submissions.get(a.getId()), today))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssignmentDetail getAssignment(UserPrincipal me, UUID assignmentId) {
        Assignment assignment = assignmentRepository.findVisibleToStudent(assignmentId, me.id())
                .orElseThrow(() -> new NotFoundException("숙제를 찾을 수 없습니다."));

        HomeworkSubmission submission = submissionRepository
                .findByAssignmentIdAndStudentId(assignmentId, me.id())
                .orElse(null);

        var comments = submission == null
                ? List.<com.purut.domain.homework.SubmissionComment>of()
                : commentRepository.findAllBySubmission(submission.getId());

        return AssignmentDetail.of(assignment, submission, comments,
                fileStorage::presignDownload, LocalDate.now());
    }

    /**
     * 제출 / 재제출. 재제출은 이미지 교체다.
     * 마감 검사는 여기서 한다 — 프론트에서만 막으면 API 직접 호출로 우회된다.
     */
    @Transactional
    public AssignmentDetail submit(UserPrincipal me, UUID assignmentId, SubmitRequest request) {
        Assignment assignment = assignmentRepository.findVisibleToStudent(assignmentId, me.id())
                .orElseThrow(() -> new NotFoundException("숙제를 찾을 수 없습니다."));

        if (assignment.isClosed(LocalDate.now())) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_CLOSED);
        }

        // 클라이언트가 보낸 키가 스토리지에 실제로 있는지 확인한다
        for (ImageRef image : request.images()) {
            if (!fileStorage.exists(image.storageKey())) {
                throw new BusinessException(ErrorCode.FILE_NOT_UPLOADED,
                        "업로드가 완료되지 않은 이미지가 있습니다.");
            }
        }

        User student = userRepository.getReferenceById(me.id());
        HomeworkSubmission submission = submissionRepository
                .findByAssignmentIdAndStudentId(assignmentId, me.id())
                .orElseGet(() -> submissionRepository.save(new HomeworkSubmission(assignment, student)));

        List<SubmissionImage> images = toImages(submission, request.images());
        submission.replaceImages(images);

        return AssignmentDetail.of(assignment, submission,
                commentRepository.findAllBySubmission(submission.getId()),
                fileStorage::presignDownload, LocalDate.now());
    }

    private List<SubmissionImage> toImages(HomeworkSubmission submission, List<ImageRef> refs) {
        return java.util.stream.IntStream.range(0, refs.size())
                .mapToObj(i -> {
                    ImageRef ref = refs.get(i);
                    return new SubmissionImage(submission, ref.storageKey(), i, ref.width(), ref.height());
                })
                .toList();
    }
}
