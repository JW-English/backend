package com.jungwoon.api.listening;

import com.jungwoon.api.auth.UserPrincipal;
import com.jungwoon.api.listening.dto.ListeningDtos.ExamListItem;
import com.jungwoon.api.listening.dto.ListeningDtos.ItemDetail;
import com.jungwoon.api.listening.dto.ListeningDtos.ItemListItem;
import com.jungwoon.api.listening.dto.ListeningDtos.ProgressRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 학생용 리스닝 API. 시험 → 문항 → 문장 순으로 파고든다. */
@RestController
@RequestMapping("/api/listening")
public class ListeningController {

    private final ListeningService listeningService;

    public ListeningController(ListeningService listeningService) {
        this.listeningService = listeningService;
    }

    @GetMapping("/exams")
    @Operation(summary = "시험 목록", description = "최신순. year 로 연도를 좁힐 수 있다")
    public List<ExamListItem> exams(@AuthenticationPrincipal UserPrincipal me,
                                    @RequestParam(required = false) Integer year) {
        return listeningService.listExams(me, year);
    }

    @GetMapping("/exams/{examId}/items")
    @Operation(summary = "문항 목록", description = "1~17번. 학습 완료 여부와 마지막 재생 위치를 함께 준다")
    public List<ItemListItem> items(@AuthenticationPrincipal UserPrincipal me,
                                    @PathVariable UUID examId) {
        return listeningService.listItems(me, examId);
    }

    @GetMapping("/items/{itemId}")
    @Operation(summary = "문항 상세",
            description = "음원 재생 URL 과 문장(영어·해석·구간)을 준다. 문장을 누르면 startMs 로 seek 한다")
    public ItemDetail item(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID itemId) {
        return listeningService.getItem(me, itemId);
    }

    @PutMapping("/items/{itemId}/progress")
    @Operation(summary = "진도 저장", description = "이어듣기 위치. 재생 중 주기적으로 호출한다")
    public ResponseEntity<Void> progress(@AuthenticationPrincipal UserPrincipal me,
                                         @PathVariable UUID itemId,
                                         @Valid @RequestBody ProgressRequest request) {
        listeningService.saveProgress(me, itemId, request);
        return ResponseEntity.noContent().build();
    }
}
