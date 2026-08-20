package com.purut.api.wiki;

import com.purut.api.wiki.dto.WikiDtos.ChapterDetail;
import com.purut.api.wiki.dto.WikiDtos.ChapterSummary;
import com.purut.api.wiki.dto.WikiDtos.TermItem;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wiki")
public class WikiController {

    private final WikiService wikiService;

    public WikiController(WikiService wikiService) {
        this.wikiService = wikiService;
    }

    @GetMapping("/chapters")
    @Operation(summary = "목차", description = "장 번호 순. 본문은 담지 않는다")
    public List<ChapterSummary> chapters() {
        return wikiService.listChapters();
    }

    @GetMapping("/chapters/{chapterId}")
    @Operation(summary = "장 본문", description = "용어 카드 목록")
    public ChapterDetail chapter(@PathVariable UUID chapterId) {
        return wikiService.getChapter(chapterId);
    }

    @GetMapping("/search")
    @Operation(summary = "용어 검색",
            description = "이름·영문명·설명을 훑는다. 두 글자 미만이면 빈 목록")
    public List<TermItem> search(@RequestParam String keyword) {
        return wikiService.search(keyword);
    }

    @GetMapping("/terms")
    @Operation(summary = "용어 일괄 조회",
            description = "즐겨찾기 복원용. 앱이 기기에 저장한 id 목록을 보낸다")
    public List<TermItem> terms(@RequestParam List<UUID> ids) {
        return wikiService.getTerms(ids);
    }
}
