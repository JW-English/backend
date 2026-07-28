package com.jungwoon.domain.homework;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, UUID> {

    Optional<HomeworkSubmission> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

    /**
     * 소유권 조건을 쿼리에 내장한 조회.
     * findById 로 가져와 나중에 검사하는 방식은 검사를 빠뜨리면 그대로 IDOR 가 된다.
     */
    Optional<HomeworkSubmission> findByIdAndStudentId(UUID id, UUID studentId);

    @Query("""
            select s from HomeworkSubmission s
            join fetch s.student
            join fetch s.assignment
            where s.assignment.id = :assignmentId
            order by s.submittedAt desc
            """)
    List<HomeworkSubmission> findAllByAssignment(UUID assignmentId);

    @Query("""
            select s from HomeworkSubmission s
            join fetch s.student
            join fetch s.assignment
            where (:status is null or s.status = :status)
            order by s.submittedAt desc
            """)
    List<HomeworkSubmission> findAllForTeacher(SubmissionStatus status);
}
