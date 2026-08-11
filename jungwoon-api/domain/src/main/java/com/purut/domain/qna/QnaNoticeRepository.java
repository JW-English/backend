package com.purut.domain.qna;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface QnaNoticeRepository extends JpaRepository<QnaNotice, UUID> {

    @Query("""
            select n
            from QnaNotice n
            where n.pinned = true
              and (n.startsAt is null or n.startsAt <= :now)
              and (n.endsAt is null or n.endsAt >= :now)
            order by n.createdAt desc
            """)
    List<QnaNotice> findActivePinned(Instant now);
}
