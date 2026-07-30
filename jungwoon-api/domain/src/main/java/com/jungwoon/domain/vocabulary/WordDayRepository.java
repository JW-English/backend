package com.jungwoon.domain.vocabulary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WordDayRepository extends JpaRepository<WordDay, UUID> {

    /**
     * 학생에게 열린 DAY 만. 예약일 조건을 쿼리에 내장해 필터링을 잊을 여지를 없앤다.
     * 다른 레벨 열람은 허용한다 — 학생이 위아래 레벨을 둘러볼 수 있어야 한다.
     *
     * DAY 1 부터 보여준다. 교재를 앞에서부터 나가므로 최신순이면 매번 끝까지
     * 스크롤해야 지금 할 DAY 가 나온다.
     */
    @Query("""
            select d from WordDay d
            where d.level = :level
              and d.scheduledDate is not null and d.scheduledDate <= :today
            order by d.dayNo asc
            """)
    List<WordDay> findOpenDays(VocabLevel level, LocalDate today);

    @Query("""
            select d from WordDay d
            where d.id = :id
              and d.scheduledDate is not null and d.scheduledDate <= :today
            """)
    Optional<WordDay> findOpenDay(UUID id, LocalDate today);

    List<WordDay> findAllByLevelOrderByDayNoDesc(VocabLevel level);

    Optional<WordDay> findByLevelAndDayNo(VocabLevel level, int dayNo);
}
