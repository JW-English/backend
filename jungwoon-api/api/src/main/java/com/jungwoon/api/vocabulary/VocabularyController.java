package com.jungwoon.api.vocabulary;

import com.jungwoon.api.auth.UserPrincipal;
import com.jungwoon.api.vocabulary.dto.VocabularyDtos.DayDetail;
import com.jungwoon.api.vocabulary.dto.VocabularyDtos.DayListItem;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vocabulary")
public class VocabularyController {

    private final VocabularyService vocabularyService;

    public VocabularyController(VocabularyService vocabularyService) {
        this.vocabularyService = vocabularyService;
    }

    @GetMapping("/days")
    @Operation(summary = "DAY 목록", description = "grade 를 비우면 내 학년. 예약일이 지난 DAY 만 보인다")
    public List<DayListItem> days(@AuthenticationPrincipal UserPrincipal me,
                                  @RequestParam(required = false) Integer grade) {
        return vocabularyService.listDays(me, grade);
    }

    @GetMapping("/days/{dayId}")
    @Operation(summary = "단어장", description = "해당 DAY 의 단어 목록. 이어 풀 응시가 있으면 그 id 도 준다")
    public DayDetail day(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID dayId) {
        return vocabularyService.getDay(me, dayId);
    }
}
