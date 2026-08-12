package com.purut.domain.vocabulary;

import com.purut.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 단어 마스터. (headword, meaning_ko) 가 유니크라 같은 단어의 다른 뜻은 별도 행이다. */
@Entity
@Getter
@Table(name = "words")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Word extends BaseEntity {

    /** 단어는 수천~수만 건이고 외부에 노출할 일이 없어 bigserial 을 그대로 쓴다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "headword", nullable = false)
    private String headword;

    @Column(name = "meaning_ko", nullable = false)
    private String meaningKo;

    @Column(name = "example_en")
    private String exampleEn;

    @Column(name = "example_ko")
    private String exampleKo;

    @Column(name = "audio_key")
    private String audioKey;

    @Column(name = "level")
    private Integer level;

    @Builder
    private Word(String headword, String meaningKo, String exampleEn, String exampleKo, Integer level) {
        this.headword = headword;
        this.meaningKo = meaningKo;
        this.exampleEn = exampleEn;
        this.exampleKo = exampleKo;
        this.level = level;
    }

    public void update(String exampleEn, String exampleKo, Integer level) {
        this.exampleEn = exampleEn;
        this.exampleKo = exampleKo;
        this.level = level;
    }
}
