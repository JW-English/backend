package com.purut.api.vocabulary;

import com.purut.api.auth.UserPrincipal;
import com.purut.api.vocabulary.dto.QuizDtos.AnswerRequest;
import com.purut.api.vocabulary.dto.QuizDtos.AttemptResponse;
import com.purut.api.vocabulary.dto.QuizDtos.ResultResponse;
import com.purut.api.vocabulary.dto.QuizDtos.StartRequest;
import com.purut.api.vocabulary.dto.QuizDtos.AttemptHistoryItem;
import com.purut.api.vocabulary.dto.QuizDtos.WrongNoteItem;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/attempts")
    @Operation(summary = "시험 시작", description = "문항을 만들어 준다. 응답에 정답은 포함되지 않는다")
    public ResponseEntity<AttemptResponse> start(@AuthenticationPrincipal UserPrincipal me,
                                                 @Valid @RequestBody StartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quizService.start(me, request));
    }

    @GetMapping("/attempts/{attemptId}")
    @Operation(summary = "이어하기", description = "중도 이탈한 시험을 이어서 푼다")
    public AttemptResponse resume(@AuthenticationPrincipal UserPrincipal me,
                                  @PathVariable UUID attemptId) {
        return quizService.getAttempt(me, attemptId);
    }

    @PostMapping("/attempts/{attemptId}/answers")
    @Operation(summary = "답 저장", description = "정오답은 알려주지 않는다. 채점은 제출 시점에 한다")
    public ResponseEntity<Void> answer(@AuthenticationPrincipal UserPrincipal me,
                                       @PathVariable UUID attemptId,
                                       @Valid @RequestBody AnswerRequest request) {
        quizService.answer(me, attemptId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @Operation(summary = "제출·채점", description = "서버가 채점하고 오답노트를 갱신한다. 중복 제출은 거부된다")
    public ResultResponse submit(@AuthenticationPrincipal UserPrincipal me,
                                 @PathVariable UUID attemptId) {
        return quizService.submit(me, attemptId);
    }

    @GetMapping("/attempts/{attemptId}/result")
    @Operation(summary = "결과 조회", description = "문항별 리뷰 (내 답 vs 정답)")
    public ResultResponse result(@AuthenticationPrincipal UserPrincipal me,
                                 @PathVariable UUID attemptId) {
        return quizService.getResult(me, attemptId);
    }

    @GetMapping("/attempts")
    @Operation(summary = "단어시험 응시 이력",
            description = "마이페이지용. 끝낸 응시만 최신순. 재응시가 쌓이므로 페이지로 끊는다")
    public List<AttemptHistoryItem> history(@AuthenticationPrincipal UserPrincipal me,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return quizService.history(me, page, size);
    }

    @GetMapping("/wrong-notes")
    @Operation(summary = "오답노트", description = "3회 연속 정답이면 졸업해 목록에서 빠진다")
    public List<WrongNoteItem> wrongNotes(@AuthenticationPrincipal UserPrincipal me) {
        return quizService.wrongNotes(me);
    }
}
