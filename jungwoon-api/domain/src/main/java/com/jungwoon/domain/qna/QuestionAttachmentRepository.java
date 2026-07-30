package com.jungwoon.domain.qna;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionAttachmentRepository extends JpaRepository<QuestionAttachment, UUID> {

    List<QuestionAttachment> findAllByQuestionIdOrderBySortOrderAsc(UUID questionId);

    List<QuestionAttachment> findAllByMessageIdInOrderBySortOrderAsc(List<UUID> messageIds);

    @EntityGraph(attributePaths = {"question.author", "message.question.author"})
    @Query("""
            select a
            from QuestionAttachment a
            left join a.question q
            left join a.message m
            left join m.question mq
            where a.id = :id
              and ((q is not null and q.deletedAt is null)
                   or (mq is not null and mq.deletedAt is null and m.deletedAt is null))
            """)
    Optional<QuestionAttachment> findReadableAttachment(@Param("id") UUID id);
}
