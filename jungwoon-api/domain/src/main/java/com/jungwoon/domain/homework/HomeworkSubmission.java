package com.jungwoon.domain.homework;

import com.jungwoon.domain.support.BaseEntity;
import com.jungwoon.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 숙제 제출물. (assignment, student) 당 1건이고 재제출은 이미지 교체다. */
@Entity
@Getter
@Table(name = "homework_submissions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomeworkSubmission extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubmissionStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<SubmissionImage> images = new ArrayList<>();

    public HomeworkSubmission(Assignment assignment, User student) {
        this.assignment = assignment;
        this.student = student;
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    /**
     * 재제출 = 이미지 교체. orphanRemoval 로 예전 이미지 행은 지워진다.
     * (스토리지의 실제 객체는 고아 파일 정리 배치가 치운다)
     */
    public void replaceImages(List<SubmissionImage> newImages) {
        images.clear();
        images.addAll(newImages);
        this.submittedAt = Instant.now();
        this.status = SubmissionStatus.SUBMITTED;
        this.reviewedAt = null;
    }

    public void markReviewed() {
        this.status = SubmissionStatus.REVIEWED;
        this.reviewedAt = Instant.now();
    }

    public void requestResubmit() {
        this.status = SubmissionStatus.RESUBMIT_REQUIRED;
        this.reviewedAt = Instant.now();
    }

    public boolean isOwnedBy(UUID studentId) {
        return student.getId().equals(studentId);
    }
}
