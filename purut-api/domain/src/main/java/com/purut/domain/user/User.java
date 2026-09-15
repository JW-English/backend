package com.purut.domain.user;

import com.purut.domain.vocabulary.VocabLevel;

import com.purut.domain.support.BaseEntity;
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

    /** 어휘 레벨. 이름은 학년을 따르지만 users.grade 와 별개다 — 고3이 고1 단어장을 볼 수 있다 */
    @Enumerated(EnumType.STRING)
    @Column(name = "vocab_level")
    private VocabLevel vocabLevel;

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

        // 어휘 레벨은 선생님이 정하지만, 비어 있으면 앱에서 어느 레벨도 선택되지
        // 않은 채로 보인다. 학년으로 일단 추정해 두고 나중에 선생님이 조정한다.
        // 이미 지정돼 있으면 건드리지 않는다 — 진급 때 다시 호출해도 유지된다
        if (this.vocabLevel == null && grade != null) {
            this.vocabLevel = switch (grade) {
                case 1 -> VocabLevel.GRADE_1;
                case 3 -> VocabLevel.GRADE_3;
                default -> VocabLevel.GRADE_2;
            };
        }
    }

    public void markLoggedIn() {
        this.lastLoginAt = Instant.now();
    }

    /** 선생님이 학생 레벨을 조정한다. 학생 본인도 설정에서 바꿀 수 있다 */
    public void changeVocabLevel(VocabLevel level) {
        this.vocabLevel = level;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
    }

    /** 소셜 전용 계정은 비밀번호가 없다 */
    public boolean hasPassword() {
        return passwordHash != null;
    }

    /** 해싱은 서비스 책임이다. 엔티티는 이미 해싱된 값만 받는다 */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /**
     * 역할 변경. 일반 API 에는 이 경로를 열지 않는다 —
     * 선생님 승급은 DB 직접 변경 또는 슈퍼관리자 전용 경로로만 한다.
     */
    public void changeRole(Role role) {
        this.role = role;
    }
}
