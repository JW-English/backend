package com.purut.domain.homework;

import com.purut.domain.common.Subject;
import com.purut.domain.support.BaseEntity;
import com.purut.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 숙제.
 *
 * 대상은 셋 중 하나다 — 특정 학생(student), 반(classId, 현재 미사용), 전체(둘 다 null).
 * 지금은 1:1 과외라 student 지정이 기본이다.
 */
@Entity
@Getter
@Table(name = "assignments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(name = "class_id")
    private UUID classId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject", nullable = false)
    private Subject subject;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "created_by")
    private UUID createdBy;

    @Builder
    private Assignment(User student, Subject subject, String title, String description,
                       LocalDate assignedDate, LocalDate dueDate, UUID createdBy) {
        this.student = student;
        this.subject = subject != null ? subject : Subject.ENGLISH;
        this.title = title;
        this.description = description;
        this.assignedDate = assignedDate != null ? assignedDate : LocalDate.now();
        this.dueDate = dueDate;
        this.createdBy = createdBy;
    }

    public void update(String title, String description, LocalDate dueDate) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
    }

    /** 마감 판단은 서버가 한다. 프론트에서만 막으면 API 를 직접 호출해 우회할 수 있다. */
    public boolean isClosed(LocalDate today) {
        return dueDate.isBefore(today);
    }

    /** 이 학생이 대상인가. student 미지정이면 전체 공지형이라 모두가 대상이다. */
    public boolean isTargeting(UUID studentId) {
        return student == null || student.getId().equals(studentId);
    }
}
