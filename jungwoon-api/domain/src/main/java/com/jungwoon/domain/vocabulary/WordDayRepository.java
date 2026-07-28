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
     * 다른 학년 열람은 허용한다 (기획 5.4) — 통계만 본인 학년 기준으로 집계한다.
     */
    @Query("""
            select d from WordDay d
            where d.grade = :grade
              and d.scheduledDate is not null and d.scheduledDate <= :today
            order by d.dayNo desc
            """)
    List<WordDay> findOpenDays(int grade, LocalDate today);

    @Query("""
            select d from WordDay d
            where d.id = :id
              and d.scheduledDate is not null and d.scheduledDate <= :today
            """)
    Optional<WordDay> findOpenDay(UUID id, LocalDate today);

    List<WordDay> findAllByGradeOrderByDayNoDesc(int grade);

    Optional<WordDay> findByGradeAndDayNo(int grade, int dayNo);
}
