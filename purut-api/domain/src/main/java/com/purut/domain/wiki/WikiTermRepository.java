package com.purut.domain.wiki;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WikiTermRepository extends JpaRepository<WikiTerm, UUID> {

    List<WikiTerm> findAllByChapterIdOrderBySortOrderAsc(UUID chapterId);

    /**
     * 용어 검색.
     *
     * 이름만 찾으면 "수동태" 는 나오는데 "be동사 + p.p." 로는 못 찾는다.
     * 영문명과 설명까지 훑는다. 253행이라 부분 일치로 충분하다.
     *
     * <p>정렬은 이름에 걸린 것을 먼저 올린다 — 설명에 스치듯 언급된 용어보다
     * 이름이 맞는 용어가 찾던 것일 가능성이 높다.
     */
    @Query("""
            select t from WikiTerm t
            join fetch t.chapter c
            where lower(t.name) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(t.nameEn, '')) like lower(concat('%', :keyword, '%'))
               or lower(t.description) like lower(concat('%', :keyword, '%'))
            order by
                case when lower(t.name) like lower(concat('%', :keyword, '%')) then 0 else 1 end,
                c.chapterNo, t.sortOrder
            """)
    List<WikiTerm> search(String keyword);

    /** 즐겨찾기 복원용. 앱이 기기에 저장한 id 목록으로 되묻는다. */
    @Query("""
            select t from WikiTerm t join fetch t.chapter
            where t.id in :ids
            """)
    List<WikiTerm> findAllByIdIn(List<UUID> ids);
}
