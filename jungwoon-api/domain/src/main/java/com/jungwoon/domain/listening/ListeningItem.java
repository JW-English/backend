package com.jungwoon.domain.listening;

import com.jungwoon.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.UUID;

/**
 * 듣기 문항.
 *
 * 음원은 문항당 파일 1개다. 문장별로 자르지 않는다 —
 * 자르면 싱크를 고칠 때마다 파일을 다시 잘라 올려야 하고,
 * 연속 재생·구간 반복·배속이 전부 복잡해진다.
 * 문장 위치는 {@link ListeningSentence} 의 start/end 밀리초로 표현한다.
 */
@Entity
@Getter
@Table(name = "listening_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningItem extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(name = "item_no", nullable = false)
    private int itemNo;

    /** 목적 · 주제 · 그림불일치 … (선택) */
    @Column(name = "item_type")
    private String itemType;

    @Column(name = "question_text")
    private String questionText;

    @Column(name = "audio_key", nullable = false)
    private String audioKey;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Builder
    private ListeningItem(Exam exam, int itemNo, String itemType, String questionText,
                          String audioKey, Integer durationMs) {
        this.exam = exam;
        this.itemNo = itemNo;
        this.itemType = itemType;
        this.questionText = questionText;
        this.audioKey = audioKey;
        this.durationMs = durationMs;
    }

    public void updateAudio(String audioKey, Integer durationMs) {
        this.audioKey = audioKey;
        this.durationMs = durationMs;
    }
}
