package com.jungwoon.domain.qna;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionMessageRepository extends JpaRepository<QuestionMessage, UUID> {

    @EntityGraph(attributePaths = {"author"})
    List<QuestionMessage> findAllByQuestionIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID questionId);

    int countByQuestionIdAndRoleAndDeletedAtIsNull(UUID questionId, QuestionMessageRole role);
}
