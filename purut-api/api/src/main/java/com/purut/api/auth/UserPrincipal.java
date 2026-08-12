package com.purut.api.auth;

import com.purut.domain.user.Role;
import com.purut.domain.user.User;

import java.util.UUID;

/**
 * 인증된 사용자. 컨트롤러는 @AuthenticationPrincipal 로 받는다.
 *
 * 3층 방어 중 3층(리소스 소유권)은 서비스에서 이 id 와 대조해 검증한다.
 * 토큰 클레임만 믿고 권한을 판단하지 말 것 — 탈퇴/정지는 DB 상태가 진실이다.
 */
public record UserPrincipal(UUID id, Role role, Integer grade) {

    public static UserPrincipal of(User user) {
        return new UserPrincipal(user.getId(), user.getRole(), user.getGrade());
    }

    public boolean isTeacher() {
        return role.isStaff();
    }
}
