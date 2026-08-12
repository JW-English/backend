package com.purut.domain.listening;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListeningItemRepository extends JpaRepository<ListeningItem, UUID> {

    List<ListeningItem> findAllByExamIdOrderByItemNoAsc(UUID examId);

    @Query("""
            select i from ListeningItem i
            join fetch i.exam
            where i.id = :id
            """)
    Optional<ListeningItem> findWithExam(UUID id);

    Optional<ListeningItem> findByExamIdAndItemNo(UUID examId, int itemNo);

    /** 시험별 문항 수. 목록 화면에서 시험마다 세면 N+1 이다. */
    @Query("""
            select new com.purut.domain.listening.ExamItemCount(i.exam.id, count(i))
            from ListeningItem i
            where i.exam.id in :examIds
            group by i.exam.id
            """)
    List<ExamItemCount> countByExams(List<UUID> examIds);
}
