package com.purut.domain.homework;

import com.purut.domain.support.BaseTimeEntity;
import com.purut.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** 선생님 첨삭 코멘트. 이미지 첨삭도 가능하다. */
@Entity
@Getter
@Table(name = "submission_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionComment extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private HomeworkSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "body")
    private String body;

    @Column(name = "image_key")
    private String imageKey;

    public SubmissionComment(HomeworkSubmission submission, User author, String body, String imageKey) {
        this.submission = submission;
        this.author = author;
        this.body = body;
        this.imageKey = imageKey;
    }
}
