package com.jungwoon.domain.qna;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    @EntityGraph(attributePaths = {"author", "refExam", "refWordDay", "refAssignment"})
    @Query("""
            select q
            from Question q
            where q.deletedAt is null
              and (:scope <> 'mine' or q.author.id = :userId)
              and (:scope = 'mine' or q.publicVisible = true or q.author.id = :userId)
              and (:category is null or q.category = :category)
              and (:status is null or q.status = :status)
              and (q.createdAt < :cursorCreatedAt
                   or (q.createdAt = :cursorCreatedAt and q.id < :cursorId))
            order by q.createdAt desc, q.id desc
            """)
    List<Question> findVisiblePage(@Param("userId") UUID userId,
                                   @Param("scope") String scope,
                                   @Param("category") QuestionCategory category,
                                   @Param("status") QuestionStatus status,
                                   @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                   @Param("cursorId") UUID cursorId,
                                   Pageable pageable);

    @EntityGraph(attributePaths = {"author", "refExam", "refWordDay", "refAssignment"})
    @Query("""
            select q
            from Question q
            where q.id = :id
              and q.deletedAt is null
              and (q.publicVisible = true or q.author.id = :userId)
            """)
    Optional<Question> findVisibleToStudent(@Param("id") UUID id, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"author", "refExam", "refWordDay", "refAssignment"})
    Optional<Question> findByIdAndAuthorIdAndDeletedAtIsNull(UUID id, UUID authorId);

    @EntityGraph(attributePaths = {"author", "refExam", "refWordDay", "refAssignment"})
    Optional<Question> findByIdAndDeletedAtIsNull(UUID id);

    @EntityGraph(attributePaths = {"author", "refExam", "refWordDay", "refAssignment"})
    @Query("""
            select q
            from Question q
            where q.deletedAt is null
              and (:status is null or q.status = :status)
              and (:status is not null or q.status in (com.jungwoon.domain.qna.QuestionStatus.PENDING, com.jungwoon.domain.qna.QuestionStatus.REOPENED))
              and (:category is null or q.category = :category)
              and (q.createdAt > :cursorCreatedAt
                   or (q.createdAt = :cursorCreatedAt and q.id > :cursorId))
            order by q.createdAt asc, q.id asc
            """)
    List<Question> findAdminQueue(@Param("status") QuestionStatus status,
                                  @Param("category") QuestionCategory category,
                                  @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    long countByDeletedAtIsNullAndStatusIn(List<QuestionStatus> statuses);
}
