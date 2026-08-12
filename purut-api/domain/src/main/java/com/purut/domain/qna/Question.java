package com.purut.domain.qna;

import com.purut.domain.common.Subject;
import com.purut.domain.homework.Assignment;
import com.purut.domain.listening.Exam;
import com.purut.domain.support.BaseEntity;
import com.purut.domain.user.User;
import com.purut.domain.vocabulary.WordDay;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject", nullable = false)
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private QuestionCategory category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "is_public", nullable = false)
    private boolean publicVisible;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QuestionStatus status;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ref_exam_id")
    private Exam refExam;

    @Column(name = "ref_item_no")
    private Integer refItemNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ref_word_day_id")
    private WordDay refWordDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ref_assignment_id")
    private Assignment refAssignment;

    @Column(name = "ref_textbook")
    private String refTextbook;

    @Column(name = "ref_page")
    private Integer refPage;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Builder
    private Question(User author, QuestionCategory category, String title, String body, boolean publicVisible,
                     Exam refExam, Integer refItemNo, WordDay refWordDay, Assignment refAssignment,
                     String refTextbook, Integer refPage) {
        this.author = author;
        this.subject = Subject.ENGLISH;
        this.category = category != null ? category : QuestionCategory.ETC;
        this.title = title;
        this.body = body;
        this.publicVisible = publicVisible;
        this.status = QuestionStatus.PENDING;
        this.refExam = refExam;
        this.refItemNo = refItemNo;
        this.refWordDay = refWordDay;
        this.refAssignment = refAssignment;
        this.refTextbook = refTextbook;
        this.refPage = refPage;
    }

    public boolean isMine(UUID userId) {
        return author.getId().equals(userId);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean canStudentRead(UUID userId) {
        return !isDeleted() && (publicVisible || isMine(userId));
    }

    public boolean canModifyBeforeAnswer(UUID userId) {
        return !isDeleted() && isMine(userId) && status == QuestionStatus.PENDING;
    }

    public void updateBeforeAnswer(String title, String body, boolean publicVisible) {
        this.title = title;
        this.body = body;
        this.publicVisible = publicVisible;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public void answer() {
        this.status = QuestionStatus.ANSWERED;
        this.answeredAt = Instant.now();
    }

    public void reopen() {
        this.status = QuestionStatus.REOPENED;
    }

    public void close() {
        this.status = QuestionStatus.CLOSED;
    }

    public void changeVisibility(boolean publicVisible) {
        this.publicVisible = publicVisible;
    }
}
