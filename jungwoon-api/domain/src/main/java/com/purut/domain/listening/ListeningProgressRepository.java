package com.purut.domain.listening;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ListeningProgressRepository
        extends JpaRepository<ListeningProgress, ListeningProgress.ListeningProgressId> {

    /** 문항 목록 화면에서 완료 표시를 그리기 위한 조회. 문항마다 조회하면 N+1 이다. */
    @Query("""
            select p from ListeningProgress p
            where p.id.studentId = :studentId and p.id.itemId in :itemIds
            """)
    List<ListeningProgress> findAllByStudentAndItems(UUID studentId, List<UUID> itemIds);

    /**
     * 시험별 완료 문항 수.
     * ListeningProgress 에는 문항 연관이 없어(복합키의 itemId 만 있음) 조건으로 조인한다.
     */
    @Query("""
            select new com.purut.domain.listening.ExamCompletedCount(i.exam.id, count(p))
            from ListeningProgress p, ListeningItem i
            where p.id.itemId = i.id
              and p.id.studentId = :studentId
              and p.completedAt is not null
              and i.exam.id in :examIds
            group by i.exam.id
            """)
    List<ExamCompletedCount> countCompletedByExams(UUID studentId, List<UUID> examIds);
}
