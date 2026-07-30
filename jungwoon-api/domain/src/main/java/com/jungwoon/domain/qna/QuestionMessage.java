package com.jungwoon.domain.qna;

import com.jungwoon.domain.support.BaseTimeEntity;
import com.jungwoon.domain.user.User;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "question_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionMessage extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private QuestionMessageRole role;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public QuestionMessage(Question question, User author, QuestionMessageRole role, String body) {
        this.question = question;
        this.author = author;
        this.role = role;
        this.body = body;
    }
}
