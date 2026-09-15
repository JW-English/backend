package com.purut.domain.wiki;

import com.purut.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** 문법 위키의 장. "1. 문장의 기본 구조" 처럼 번호와 제목을 가진다. */
@Entity
@Getter
@Table(name = "wiki_chapters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WikiChapter extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(name = "title", nullable = false)
    private String title;

    @Builder
    private WikiChapter(int chapterNo, String title) {
        this.chapterNo = chapterNo;
        this.title = title;
    }

    public void update(String title) {
        this.title = title;
    }
}
