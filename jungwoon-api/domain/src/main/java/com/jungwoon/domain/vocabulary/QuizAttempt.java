package com.jungwoon.domain.vocabulary;

import com.jungwoon.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 단어시험 응시.
 *
 * 재응시는 무제한이다 — 응시할 때마다 새 행이 쌓이고, 최초·최고 점수를 모두 볼 수 있다.
 * (선생님 평가 기준은 최초 점수)
 */
@Entity
@Getter
@Table(name = "quiz_attempts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAttempt {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_id", nullable = false)
    private WordDay day;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** 채워져 있으면 제출이 끝난 것 — 재제출을 거부하는 기준이다. */
    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    /**
     * DB 생성 컬럼(GENERATED ALWAYS). 애플리케이션은 읽기만 한다.
     *
     * @Generated 가 없으면 채점 직후 엔티티의 값이 낡은 채로 응답에 실린다 —
     * DB 에는 20점이 들어갔는데 화면에는 0점이 뜬다.
     */
    @org.hibernate.annotations.Generated(event = {
            org.hibernate.generator.EventType.INSERT,
            org.hibernate.generator.EventType.UPDATE
    })
    @Column(name = "score", insertable = false, updatable = false)
    private BigDecimal score;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<QuizAnswer> answers = new ArrayList<>();

    public QuizAttempt(User student, WordDay day) {
        this.student = student;
        this.day = day;
        this.startedAt = Instant.now();
    }

    public void addAnswer(QuizAnswer answer) {
        answers.add(answer);
        this.totalCount = answers.size();
    }

    public boolean isFinished() {
        return finishedAt != null;
    }

    public boolean isOwnedBy(UUID studentId) {
        return student.getId().equals(studentId);
    }

    /** 채점은 서버 전용이다. 클라이언트가 보낸 정오답은 신뢰하지 않는다. */
    public void finish(int correctCount) {
        this.correctCount = correctCount;
        this.finishedAt = Instant.now();
    }
}
