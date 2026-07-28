package com.jungwoon.domain.listening;

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
}
