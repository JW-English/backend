package com.purut.api.qna;

import com.purut.api.auth.UserPrincipal;
import com.purut.api.qna.dto.QnaDtos.AttachmentUrl;
import com.purut.api.qna.dto.QnaDtos.CreateMessageRequest;
import com.purut.api.qna.dto.QnaDtos.CreateQuestionRequest;
import com.purut.api.qna.dto.QnaDtos.CursorPage;
import com.purut.api.qna.dto.QnaDtos.QnaNoticeItem;
import com.purut.api.qna.dto.QnaDtos.QuestionDetail;
import com.purut.api.qna.dto.QnaDtos.QuestionListItem;
import com.purut.api.qna.dto.QnaDtos.UpdateQuestionRequest;
import com.purut.domain.qna.QuestionCategory;
import com.purut.domain.qna.QuestionStatus;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@RestController
public class QnaController {

    private final QnaService qnaService;

    public QnaController(QnaService qnaService) {
        this.qnaService = qnaService;
    }

    @GetMapping("/api/questions")
    @Operation(summary = "Q&A 목록", description = "공개 질문 또는 내 질문을 커서 기반으로 조회한다")
    public CursorPage<QuestionListItem> list(@AuthenticationPrincipal UserPrincipal me,
                                             @RequestParam(defaultValue = "public") String scope,
                                             @RequestParam(required = false) QuestionCategory category,
                                             @RequestParam(required = false) QuestionStatus status,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false) Integer size) {
        return qnaService.list(me, scope, category, status, cursor, size);
    }

    @PostMapping("/api/questions")
    @Operation(summary = "질문 작성")
    public ResponseEntity<QuestionDetail> create(@AuthenticationPrincipal UserPrincipal me,
                                                 @Valid @RequestBody CreateQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(qnaService.create(me, request));
    }

    @GetMapping("/api/questions/{questionId}")
    @Operation(summary = "질문 상세")
    public QuestionDetail detail(@AuthenticationPrincipal UserPrincipal me,
                                 @PathVariable UUID questionId) {
        return qnaService.detail(me, questionId);
    }

    @PatchMapping("/api/questions/{questionId}")
    @Operation(summary = "질문 수정", description = "본인 질문만, 답변 전까지만 가능")
    public QuestionDetail update(@AuthenticationPrincipal UserPrincipal me,
                                 @PathVariable UUID questionId,
                                 @Valid @RequestBody UpdateQuestionRequest request) {
        return qnaService.update(me, questionId, request);
    }

    @DeleteMapping("/api/questions/{questionId}")
    @Operation(summary = "질문 삭제", description = "본인 질문만, 답변 전까지만 soft delete")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal me,
                                       @PathVariable UUID questionId) {
        qnaService.delete(me, questionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/questions/{questionId}/messages")
    @Operation(summary = "재질문")
    public QuestionDetail reopen(@AuthenticationPrincipal UserPrincipal me,
                                 @PathVariable UUID questionId,
                                 @Valid @RequestBody CreateMessageRequest request) {
        return qnaService.reopen(me, questionId, request);
    }

    @PostMapping("/api/questions/{questionId}/close")
    @Operation(summary = "해결됨 표시")
    public QuestionDetail close(@AuthenticationPrincipal UserPrincipal me,
                                @PathVariable UUID questionId) {
        return qnaService.close(me, questionId);
    }

    @GetMapping("/api/questions/attachments/{attachmentId}/url")
    @Operation(summary = "Q&A 첨부 다운로드 URL", description = "질문 열람 권한을 재검증한 뒤 만료형 URL을 발급한다")
    public AttachmentUrl attachmentUrl(@AuthenticationPrincipal UserPrincipal me,
                                       @PathVariable UUID attachmentId) {
        return qnaService.attachmentUrl(me, attachmentId);
    }

    @GetMapping("/api/qna/notices")
    @Operation(summary = "Q&A 공지")
    public List<QnaNoticeItem> notices() {
        return qnaService.notices();
    }
}
