package com.purut.api.wiki;

import com.purut.api.wiki.dto.WikiDtos.ChapterDetail;
import com.purut.api.wiki.dto.WikiDtos.ChapterSummary;
import com.purut.api.wiki.dto.WikiDtos.TermItem;
import com.purut.common.error.NotFoundException;
import com.purut.domain.wiki.WikiChapter;
import com.purut.domain.wiki.WikiChapterRepository;
import com.purut.domain.wiki.WikiTerm;
import com.purut.domain.wiki.WikiTermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 문법 위키 조회. 학생 쪽은 읽기 전용이다. */
@Service
public class WikiService {

    /** 검색어가 한 글자면 결과가 수십 개라 의미가 없다. */
    private static final int MIN_KEYWORD = 2;

    private final WikiChapterRepository chapterRepository;
    private final WikiTermRepository termRepository;

    public WikiService(WikiChapterRepository chapterRepository,
                       WikiTermRepository termRepository) {
        this.chapterRepository = chapterRepository;
        this.termRepository = termRepository;
    }

    /** 목차. 용어 수를 한 번에 세어 30번 나가는 것을 막는다. */
    @Transactional(readOnly = true)
    public List<ChapterSummary> listChapters() {
        Map<UUID, Long> counts = chapterRepository.countTermsByChapter().stream()
                .collect(Collectors.toMap(
                        WikiChapterRepository.ChapterTermCount::getChapterId,
                        WikiChapterRepository.ChapterTermCount::getTermCount));

        return chapterRepository.findAllByOrderByChapterNoAsc().stream()
                .map(c -> ChapterSummary.of(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChapterDetail getChapter(UUID chapterId) {
        WikiChapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 챕터입니다."));
        return ChapterDetail.of(chapter, termRepository.findAllByChapterIdOrderBySortOrderAsc(chapterId));
    }

    @Transactional(readOnly = true)
    public List<TermItem> search(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.length() < MIN_KEYWORD) {
            return List.of();
        }
        return termRepository.search(trimmed).stream().map(TermItem::of).toList();
    }

    /**
     * 즐겨찾기 복원.
     *
     * 즐겨찾기 자체는 기기에 저장한다. 앱이 가진 id 목록으로 내용을 되묻는 것이라
     * 순서는 앱이 준 순서를 지킨다 — 담은 순서가 사용자에게 의미가 있다.
     */
    @Transactional(readOnly = true)
    public List<TermItem> getTerms(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, WikiTerm> found = termRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(WikiTerm::getId, t -> t));

        return ids.stream()
                .map(found::get)
                .filter(java.util.Objects::nonNull)   // 지워진 용어는 조용히 뺀다
                .map(TermItem::of)
                .toList();
    }
}
