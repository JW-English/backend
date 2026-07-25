package com.jungwoon.domain.user;

import com.jungwoon.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    /**
     * 랜덤 UUIDv4 는 인덱스 단편화를 만든다.
     * Hibernate 의 시간 정렬형(Style.TIME) 생성기를 써서 삽입 순서와 키 순서를 맞춘다.
     */
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** 소셜 전용 계정은 NULL 가능. */
    @Column(name = "email", unique = true)
    private String email;

    /** 소셜 전용 계정은 NULL. BCrypt(strength 12). */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    /** 1, 2, 3 (고1~고3). 온보딩 전에는 NULL. */
    @Column(name = "grade")
    private Integer grade;

    @Column(name = "school")
    private String school;

    @Column(name = "avatar_key")
    private String avatarKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    /** 프로필 설정 완료 시각. NULL 이면 앱이 온보딩 화면으로 보낸다. */
    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Builder
    private User(String email, String passwordHash, Role role, String name, String phone,
                 Integer grade, String school) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : Role.STUDENT;
        this.name = name;
        this.phone = phone;
        this.grade = grade;
        this.school = school;
        this.status = UserStatus.ACTIVE;
    }

    public boolean isOnboarded() {
        return onboardedAt != null;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void completeOnboarding(String name, Integer grade, String school) {
        this.name = name;
        this.grade = grade;
        this.school = school;
        this.onboardedAt = Instant.now();
    }

    public void markLoggedIn() {
        this.lastLoginAt = Instant.now();
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
    }
}
