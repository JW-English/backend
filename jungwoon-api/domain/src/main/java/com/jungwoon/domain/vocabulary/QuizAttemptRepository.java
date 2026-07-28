package com.jungwoon.domain.vocabulary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    /** 소유권 조건을 쿼리에 내장한다 — 남의 응시를 열어보는 경로를 구조적으로 막는다. */
    @Query("""
            select a from QuizAttempt a
            join fetch a.day
            where a.id = :id and a.student.id = :studentId
            """)
    Optional<QuizAttempt> findMine(UUID id, UUID studentId);

    @Query("""
            select a from QuizAttempt a
            where a.student.id = :studentId and a.day.id = :dayId and a.finishedAt is not null
            order by a.startedAt asc
            """)
    List<QuizAttempt> findFinished(UUID studentId, UUID dayId);

    /** DAY 목록 화면에서 상태 뱃지를 그리기 위한 조회. */
    @Query("""
            select a from QuizAttempt a
            where a.student.id = :studentId and a.day.id in :dayIds and a.finishedAt is not null
            """)
    List<QuizAttempt> findFinishedByDays(UUID studentId, List<UUID> dayIds);
}
