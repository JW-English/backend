package com.purut.domain.listening;

import com.purut.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** 듣기 시험 (2026 수능, 9월 모평 …). */
@Entity
@Getter
@Table(name = "exams")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exam extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "year", nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false)
    private ExamType examType;

    @Column(name = "grade", nullable = false)
    private int grade;

    @Column(name = "title", nullable = false)
    private String title;

    /** 전체 음원. 문항별 음원이 따로 있으므로 선택이다. */
    @Column(name = "audio_key")
    private String audioKey;

    @Builder
    private Exam(int year, ExamType examType, int grade, String title, String audioKey) {
        this.year = year;
        this.examType = examType;
        this.grade = grade;
        this.title = title;
        this.audioKey = audioKey;
    }
}
