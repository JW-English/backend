package com.jungwoon.domain.listening;

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
}
