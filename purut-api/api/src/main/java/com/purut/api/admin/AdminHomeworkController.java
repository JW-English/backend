package com.purut.api.admin;

import com.purut.api.admin.dto.AdminHomeworkDtos.AdminAssignmentItem;
import com.purut.api.admin.dto.AdminHomeworkDtos.AdminSubmissionDetail;
import com.purut.api.admin.dto.AdminHomeworkDtos.AdminSubmissionItem;
import com.purut.api.admin.dto.AdminHomeworkDtos.CommentRequest;
import com.purut.api.admin.dto.AdminHomeworkDtos.CreateAssignmentRequest;
import com.purut.api.admin.dto.AdminHomeworkDtos.UpdateAssignmentRequest;
import com.purut.api.auth.UserPrincipal;
import com.purut.domain.homework.SubmissionStatus;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 선생님 전용 숙제 API.
 *
 * URL 인가(/api/admin/**)에 더해 메서드 인가를 겹친다 — 1층이 뚫려도 2층이 남는다.
 */
@RestController
@RequestMapping("/api/admin/homework")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class AdminHomeworkController {

    private final AdminHomeworkService adminHomeworkService;

    public AdminHomeworkController(AdminHomeworkService adminHomeworkService) {
        this.adminHomeworkService = adminHomeworkService;
    }

    @PostMapping("/assignments")
    @Operation(summary = "숙제 생성", description = "studentId 를 비우면 전체 학생 대상(공지형)")
    public ResponseEntity<AdminAssignmentItem> create(@AuthenticationPrincipal UserPrincipal me,
                                                      @Valid @RequestBody CreateAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminHomeworkService.create(me, request));
    }

    @PatchMapping("/assignments/{assignmentId}")
    @Operation(summary = "숙제 수정")
    public AdminAssignmentItem update(@PathVariable UUID assignmentId,
                                      @Valid @RequestBody UpdateAssignmentRequest request) {
        return adminHomeworkService.update(assignmentId, request);
    }

    @GetMapping("/assignments")
    @Operation(summary = "숙제 목록", description = "studentId 로 특정 학생 것만 볼 수 있다")
    public List<AdminAssignmentItem> listAssignments(@RequestParam(required = false) UUID studentId) {
        return adminHomeworkService.listAssignments(studentId);
    }

    @GetMapping("/submissions")
    @Operation(summary = "제출 현황", description = "status 로 미첨삭(SUBMITTED)만 걸러 볼 수 있다")
    public List<AdminSubmissionItem> listSubmissions(@RequestParam(required = false) SubmissionStatus status) {
        return adminHomeworkService.listSubmissions(status);
    }

    @GetMapping("/submissions/{submissionId}")
    @Operation(summary = "제출물 상세", description = "제출 사진을 만료형 URL 로 준다")
    public AdminSubmissionDetail getSubmission(@PathVariable UUID submissionId) {
        return adminHomeworkService.getSubmission(submissionId);
    }

    @PostMapping("/submissions/{submissionId}/comments")
    @Operation(summary = "첨삭 코멘트", description = "코멘트를 달면 첨삭완료로 바뀐다. requestResubmit=true 면 재제출 요청")
    public ResponseEntity<Void> comment(@AuthenticationPrincipal UserPrincipal me,
                                        @PathVariable UUID submissionId,
                                        @Valid @RequestBody CommentRequest request) {
        adminHomeworkService.comment(me, submissionId, request);
        return ResponseEntity.noContent().build();
    }
}
