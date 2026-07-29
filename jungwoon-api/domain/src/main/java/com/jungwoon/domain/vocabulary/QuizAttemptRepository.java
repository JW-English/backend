package com.jungwoon.domain.vocabulary;

import com.jungwoon.domain.user.StudentSummaryProjections;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
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

    /**
     * 아직 제출하지 않은 응시. 중도 이탈 후 이어 풀 수 있게 하려면 진입점이 필요하다.
     * 이게 없으면 나간 응시는 그대로 버려진다.
     */
    @Query("""
            select a from QuizAttempt a
            where a.student.id = :studentId and a.day.id in :dayIds and a.finishedAt is null
            order by a.startedAt desc
            """)
    List<QuizAttempt> findInProgressByDays(UUID studentId, List<UUID> dayIds);

    /**
     * 마이페이지 단어시험 이력. 끝낸 응시만, 최신순.
     * DAY 이름을 함께 보여주므로 fetch join 한다.
     */
    @Query("""
            select a from QuizAttempt a
            join fetch a.day
            where a.student.id = :studentId and a.finishedAt is not null
            order by a.finishedAt desc
            """)
    List<QuizAttempt> findHistory(UUID studentId, Pageable pageable);

    /** 평균·최고점 요약. 목록을 읽어 계산하지 않고 집계로 끝낸다 */
    @Query("""
            select count(a)      as attemptCount,
                   avg(a.score)  as averageScore,
                   max(a.score)  as bestScore
            from QuizAttempt a
            where a.student.id = :studentId and a.finishedAt is not null
            """)
    StudentSummaryProjections.QuizStats summarizeForStudent(UUID studentId);
}
