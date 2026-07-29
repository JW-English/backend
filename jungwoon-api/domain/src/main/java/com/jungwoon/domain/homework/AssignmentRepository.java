package com.jungwoon.domain.homework;

import com.jungwoon.domain.user.StudentSummaryProjections;
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

    /**
     * 마이페이지 숙제 제출률. 목록을 읽어 세지 않고 집계 한 번으로 끝낸다.
     *
     * 분모는 학생이 볼 수 있는 숙제 전체이고, 제출 여부는 좌측 조인으로 센다.
     */
    @Query("""
            select count(a)                                                as total,
                   count(s.id)                                             as submitted,
                   count(case when s.status = com.jungwoon.domain.homework.SubmissionStatus.REVIEWED
                              then 1 end)                                  as reviewed
            from Assignment a
            left join HomeworkSubmission s
                   on s.assignment.id = a.id and s.student.id = :studentId
            where a.student.id = :studentId or (a.student is null and a.classId is null)
            """)
    StudentSummaryProjections.HomeworkCounts summarizeForStudent(UUID studentId);
}
