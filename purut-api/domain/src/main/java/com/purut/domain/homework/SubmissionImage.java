package com.purut.domain.homework;

import com.purut.domain.support.BaseTimeEntity;
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

@Entity
@Getter
@Table(name = "submission_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionImage extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private HomeworkSubmission submission;

    /** 스토리지 객체 키. URL 이 아니라 키를 저장한다 — presigned URL 은 만료되기 때문. */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    public SubmissionImage(HomeworkSubmission submission, String storageKey, int sortOrder,
                           Integer width, Integer height) {
        this.submission = submission;
        this.storageKey = storageKey;
        this.sortOrder = sortOrder;
        this.width = width;
        this.height = height;
    }
}
