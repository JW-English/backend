package com.purut.api.wiki.dto;

import com.purut.domain.wiki.WikiChapter;
import com.purut.domain.wiki.WikiTerm;

import java.util.List;
import java.util.UUID;

public final class WikiDtos {

    private WikiDtos() {
    }

    /** 목차 한 줄. 본문은 담지 않는다 — 30장을 한 번에 내려보내면 응답이 커진다. */
    public record ChapterSummary(UUID id, int chapterNo, String title, long termCount) {
        public static ChapterSummary of(WikiChapter chapter, long termCount) {
            return new ChapterSummary(chapter.getId(), chapter.getChapterNo(),
                    chapter.getTitle(), termCount);
        }
    }

    /** 용어 카드. 화면의 블록 하나에 대응한다. */
    public record TermItem(
            UUID id,
            String name,
            String nameEn,
            String description,
            List<String> usages,
            /** 예문과 해석은 같은 길이다. 화면에서 짝으로 붙여 보여준다 */
            List<String> examples,
            List<String> meanings,
            int chapterNo,
            String chapterTitle
    ) {
        public static TermItem of(WikiTerm term) {
            return new TermItem(
                    term.getId(), term.getName(), term.getNameEn(), term.getDescription(),
                    term.getUsages() == null ? List.of() : term.getUsages(),
                    term.getExamples(), term.getMeanings(),
                    term.getChapter().getChapterNo(), term.getChapter().getTitle());
        }
    }

    public record ChapterDetail(UUID id, int chapterNo, String title, List<TermItem> terms) {
        public static ChapterDetail of(WikiChapter chapter, List<WikiTerm> terms) {
            return new ChapterDetail(chapter.getId(), chapter.getChapterNo(), chapter.getTitle(),
                    terms.stream().map(TermItem::of).toList());
        }
    }
}
