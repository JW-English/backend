package com.jungwoon.domain.homework;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    /**
     * 학생이 볼 수 있는 숙제 = 본인 지정 + 전체 대상.
     * 조건을 쿼리에 박아둔다 — 서비스에서 필터링을 잊을 여지를 없앤다.
     */
    @Query("""
            select a from Assignment a
            where (a.student.id = :studentId or (a.student is null and a.classId is null))
              and a.dueDate between :from and :to
            order by a.dueDate desc
            """)
    List<Assignment> findVisibleToStudent(UUID studentId, LocalDate from, LocalDate to);

    @Query("""
            select a from Assignment a
            where a.id = :id
              and (a.student.id = :studentId or (a.student is null and a.classId is null))
            """)
    Optional<Assignment> findVisibleToStudent(UUID id, UUID studentId);

    @Query("""
            select a from Assignment a
            left join fetch a.student
            where (:studentId is null or a.student.id = :studentId)
            order by a.dueDate desc
            """)
    List<Assignment> findAllForTeacher(UUID studentId);
}
