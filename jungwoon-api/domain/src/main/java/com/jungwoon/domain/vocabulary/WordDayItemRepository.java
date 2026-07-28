package com.jungwoon.domain.vocabulary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WordDayItemRepository
        extends JpaRepository<WordDayItem, WordDayItem.WordDayItemId> {

    /** 단어장 조회. fetch join 으로 단어까지 한 번에 가져온다 (N+1 방지). */
    @Query("""
            select i from WordDayItem i
            join fetch i.word
            where i.day.id = :dayId
            order by i.sortOrder asc
            """)
    List<WordDayItem> findAllByDay(UUID dayId);

    void deleteByDayId(UUID dayId);

    long countByDayId(UUID dayId);

    @Query("""
            select new com.jungwoon.domain.vocabulary.DayWordCount(i.day.id, count(i))
            from WordDayItem i
            where i.day.id in :dayIds
            group by i.day.id
            """)
    List<DayWordCount> countByDays(List<UUID> dayIds);
}
