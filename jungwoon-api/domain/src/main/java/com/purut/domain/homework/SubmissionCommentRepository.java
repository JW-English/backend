package com.purut.domain.homework;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SubmissionCommentRepository extends JpaRepository<SubmissionComment, UUID> {

    @Query("""
            select c from SubmissionComment c
            left join fetch c.author
            where c.submission.id = :submissionId
            order by c.createdAt asc
            """)
    List<SubmissionComment> findAllBySubmission(UUID submissionId);
}
