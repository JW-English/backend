package com.purut.domain.wiki;

import com.purut.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.List;
import java.util.UUID;

/**
 * 용어 하나. 화면에서는 카드 한 장이다.
 *
 * <p>{@code examples} 와 {@code meanings} 는 짝이다. 비교 예문이 있는 용어는
 * 두 줄씩 들어간다 — "I stopped smoking." / "I stopped to smoke." 처럼.
 * 개수가 어긋나면 화면에서 해석이 밀리므로 DB 제약으로도 막아둔다.
 */
@Entity
@Table(name = "wiki_terms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WikiTerm extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private WikiChapter chapter;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "name", nullable = false)
    private String name;

    /** 영문명. 8장 조동사처럼 원문에 없는 챕터가 있다. */
    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "description", nullable = false)
    private String description;

    // Postgres text[] 에는 String[] 을 쓴다. List<String> 에 @JdbcTypeCode(ARRAY) 를
    // 붙이면 Hibernate 가 jsonb 로 해석해 스키마 검증에서 걸린다.

    /** "ex) run, eat, know" 목록. 없는 용어가 더 많다. */
    @Column(name = "usages")
    private String[] usages;

    @Column(name = "examples", nullable = false)
    private String[] examples;

    @Column(name = "meanings", nullable = false)
    private String[] meanings;

    @Builder
    private WikiTerm(WikiChapter chapter, int sortOrder, String name, String nameEn,
                     String description, String[] usages,
                     String[] examples, String[] meanings) {
        this.chapter = chapter;
        this.sortOrder = sortOrder;
        this.name = name;
        this.nameEn = nameEn;
        this.description = description;
        this.usages = usages;
        this.examples = examples;
        this.meanings = meanings;
    }

    public UUID getId() {
        return id;
    }

    public WikiChapter getChapter() {
        return chapter;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getDescription() {
        return description;
    }

    // 배열을 그대로 넘기면 호출한 쪽에서 내용을 바꿀 수 있다. 읽기 전용으로 감싼다
    public List<String> getUsages() {
        return usages == null ? List.of() : List.of(usages);
    }

    public List<String> getExamples() {
        return List.of(examples);
    }

    public List<String> getMeanings() {
        return List.of(meanings);
    }
}
