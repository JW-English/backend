package com.purut.domain.vocabulary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WordRepository extends JpaRepository<Word, Long> {

    boolean existsByHeadwordAndMeaningKo(String headword, String meaningKo);

    /** 오답 보기 보충용 — 같은 난이도 풀에서 가져온다. */
    @Query("""
            select w from Word w
            where w.id not in :excludeIds
              and (:level is null or w.level = :level)
            order by function('random')
            limit :limit
            """)
    List<Word> findRandomExcluding(List<Long> excludeIds, Integer level, int limit);
}
