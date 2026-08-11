package com.purut.api.homework;

import com.purut.api.auth.UserPrincipal;
import com.purut.api.homework.dto.HomeworkDtos.AssignmentDetail;
import com.purut.api.homework.dto.HomeworkDtos.AssignmentListItem;
import com.purut.api.homework.dto.HomeworkDtos.SubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 학생용 숙제 API. 선생님 기능은 /api/admin/homework 로 분리돼 있다. */
@RestController
@RequestMapping("/api/homework")
public class HomeworkController {

    private final HomeworkService homeworkService;

    public HomeworkController(HomeworkService homeworkService) {
        this.homeworkService = homeworkService;
    }

    @GetMapping("/assignments")
    @Operation(summary = "숙제 목록", description = "캘린더용. 기간 안의 숙제와 내 제출 상태를 함께 준다")
    public List<AssignmentListItem> list(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return homeworkService.listAssignments(me, from, to);
    }

    @GetMapping("/assignments/{assignmentId}")
    @Operation(summary = "숙제 상세", description = "내 제출물과 선생님 코멘트를 포함한다")
    public AssignmentDetail detail(@AuthenticationPrincipal UserPrincipal me,
                                   @PathVariable UUID assignmentId) {
        return homeworkService.getAssignment(me, assignmentId);
    }

    @PostMapping("/assignments/{assignmentId}/submission")
    @Operation(summary = "숙제 제출", description = "presign 으로 올린 이미지 키를 등록한다. 마감 후에는 거부된다")
    public AssignmentDetail submit(@AuthenticationPrincipal UserPrincipal me,
                                   @PathVariable UUID assignmentId,
                                   @Valid @RequestBody SubmitRequest request) {
        return homeworkService.submit(me, assignmentId, request);
    }

    @PutMapping("/assignments/{assignmentId}/submission")
    @Operation(summary = "제출 사진 교체", description = "재제출은 이미지 교체다. 마감 전까지만 가능하다")
    public AssignmentDetail resubmit(@AuthenticationPrincipal UserPrincipal me,
                                     @PathVariable UUID assignmentId,
                                     @Valid @RequestBody SubmitRequest request) {
        return homeworkService.submit(me, assignmentId, request);
    }
}
