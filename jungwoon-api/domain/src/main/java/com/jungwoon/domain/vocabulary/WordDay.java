package com.jungwoon.domain.vocabulary;

import com.jungwoon.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

/** 학년별 날짜 단위 학습 묶음 ("DAY 12"). */
@Entity
@Getter
@Table(name = "word_days")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WordDay extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "grade", nullable = false)
    private int grade;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    /** 이 날짜부터 학생에게 열린다. NULL 이면 아직 공개하지 않은 것으로 본다. */
    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "title")
    private String title;

    @Builder
    private WordDay(int grade, int dayNo, LocalDate scheduledDate, String title) {
        this.grade = grade;
        this.dayNo = dayNo;
        this.scheduledDate = scheduledDate;
        this.title = title;
    }

    public void update(String title, LocalDate scheduledDate) {
        this.title = title;
        this.scheduledDate = scheduledDate;
    }

    /**
     * 학생에게 열렸는가. 예약일이 없거나 아직 오지 않았으면 닫혀 있다.
     * 이 판단을 클라이언트에 맡기면 미공개 단어를 미리 볼 수 있다.
     */
    public boolean isOpen(LocalDate today) {
        return scheduledDate != null && !scheduledDate.isAfter(today);
    }
}
