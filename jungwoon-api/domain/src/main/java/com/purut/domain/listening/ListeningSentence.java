package com.purut.domain.listening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 스크립트 문장 1개.
 *
 * start/end 밀리초는 강제 정렬(WhisperX)로 뽑고 관리자 화면에서 보정한다.
 * 앱은 이 값으로 단일 음원 안에서 seek 한다 — 문장을 누르면 그 지점부터 재생된다.
 */
@Entity
@Getter
@Table(name = "listening_sentences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningSentence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private ListeningItem item;

    @Column(name = "seq", nullable = false)
    private int seq;

    /** M | W | NARRATOR */
    @Column(name = "speaker")
    private String speaker;

    @Column(name = "text_en", nullable = false)
    private String textEn;

    /** 해석. 토글로 보여준다. */
    @Column(name = "text_ko")
    private String textKo;

    @Column(name = "start_ms", nullable = false)
    private int startMs;

    @Column(name = "end_ms", nullable = false)
    private int endMs;

    @Builder
    private ListeningSentence(ListeningItem item, int seq, String speaker, String textEn,
                              String textKo, int startMs, int endMs) {
        this.item = item;
        this.seq = seq;
        this.speaker = speaker;
        this.textEn = textEn;
        this.textKo = textKo;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    /** 싱크 에디터에서 ±수백 ms 보정할 때 쓴다. */
    public void updateTiming(int startMs, int endMs) {
        this.startMs = startMs;
        this.endMs = endMs;
    }

    public void updateTranslation(String textKo) {
        this.textKo = textKo;
    }
}
