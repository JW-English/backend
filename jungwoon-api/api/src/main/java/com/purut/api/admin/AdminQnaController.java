package com.purut.api.admin;

import com.purut.api.auth.UserPrincipal;
import com.purut.api.qna.QnaService;
import com.purut.api.qna.dto.AdminQnaDtos.AdminQuestionDetail;
import com.purut.api.qna.dto.AdminQnaDtos.AdminQuestionListItem;
import com.purut.api.qna.dto.QnaDtos.CreateMessageRequest;
import com.purut.api.qna.dto.QnaDtos.CursorPage;
import com.purut.api.qna.dto.QnaDtos.VisibilityRequest;
import com.purut.domain.qna.QuestionCategory;
import com.purut.domain.qna.QuestionStatus;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/questions")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class AdminQnaController {

    private final QnaService qnaService;

    public AdminQnaController(QnaService qnaService) {
        this.qnaService = qnaService;
    }

    @GetMapping
    @Operation(summary = "관리자 Q&A 큐", description = "기본은 PENDING·REOPENED 오래 기다린 순")
    public CursorPage<AdminQuestionListItem> list(@RequestParam(required = false) QuestionStatus status,
                                                  @RequestParam(required = false) QuestionCategory category,
                                                  @RequestParam(required = false) String cursor,
                                                  @RequestParam(required = false) Integer size) {
        return qnaService.adminList(status, category, cursor, size);
    }

    @GetMapping("/summary")
    @Operation(summary = "관리자 Q&A 요약")
    public Map<String, Long> summary() {
        return Map.of("pendingCount", qnaService.adminPendingCount());
    }

    @GetMapping("/{questionId}")
    @Operation(summary = "관리자 Q&A 상세")
    public AdminQuestionDetail detail(@PathVariable UUID questionId) {
        return qnaService.adminDetail(questionId);
    }

    @PostMapping("/{questionId}/messages")
    @Operation(summary = "답변 작성")
    public AdminQuestionDetail answer(@AuthenticationPrincipal UserPrincipal me,
                                      @PathVariable UUID questionId,
                                      @Valid @RequestBody CreateMessageRequest request) {
        return qnaService.answer(me, questionId, request);
    }

    @PatchMapping("/{questionId}/visibility")
    @Operation(summary = "공개 여부 전환")
    public AdminQuestionDetail visibility(@PathVariable UUID questionId,
                                          @RequestBody VisibilityRequest request) {
        return qnaService.changeVisibility(questionId, request.publicVisible());
    }
}
