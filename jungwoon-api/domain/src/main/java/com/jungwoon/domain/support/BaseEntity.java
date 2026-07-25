package com.jungwoon.domain.support;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

/** created_at + updated_at 를 갖는 테이블용. */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
