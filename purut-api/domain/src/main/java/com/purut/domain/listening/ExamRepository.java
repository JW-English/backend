package com.purut.domain.listening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    /** 최신 시험이 위로. 연도 필터는 선택이다. */
    List<Exam> findAllByOrderByYearDescExamTypeAsc();

    List<Exam> findAllByYearOrderByExamTypeAsc(int year);

    Optional<Exam> findByYearAndExamTypeAndGrade(int year, ExamType examType, int grade);
}
