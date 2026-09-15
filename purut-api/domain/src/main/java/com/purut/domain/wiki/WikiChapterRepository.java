package com.purut.domain.wiki;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WikiChapterRepository extends JpaRepository<WikiChapter, UUID> {

    List<WikiChapter> findAllByOrderByChapterNoAsc();

    /**
     * 목록 화면에 쓸 장별 용어 수.
     *
     * 장마다 count 를 세면 30번 나간다. 한 번에 세어 온다.
     */
    @Query("""
            select c.id as chapterId, count(t.id) as termCount
            from WikiChapter c left join WikiTerm t on t.chapter = c
            group by c.id
            """)
    List<ChapterTermCount> countTermsByChapter();

    interface ChapterTermCount {
        UUID getChapterId();
        long getTermCount();
    }
}
