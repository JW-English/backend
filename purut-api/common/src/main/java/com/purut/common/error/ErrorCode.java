package com.purut.common.error;

/**
 * 서비스 전역 에러 코드.
 * status 는 HTTP 상태 코드지만 common 모듈이 spring-web 에 의존하지 않도록 int 로 둔다.
 */
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(400, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "권한이 없습니다."),
    NOT_FOUND(404, "대상을 찾을 수 없습니다."),
    CONFLICT(409, "이미 처리된 요청입니다."),
    INTERNAL_ERROR(500, "일시적인 오류가 발생했습니다."),

    // 인증
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(401, "만료된 토큰입니다."),
    REFRESH_TOKEN_REUSED(401, "토큰이 재사용되었습니다. 다시 로그인해 주세요."),
    ACCOUNT_LOCKED(423, "로그인 시도가 많아 잠긴 계정입니다. 잠시 후 다시 시도해 주세요."),
    SOCIAL_ALREADY_LINKED(409, "이미 다른 방법으로 가입된 계정입니다."),
    WITHDRAWN_USER(403, "탈퇴한 계정입니다."),
    PASSWORD_MISMATCH(400, "현재 비밀번호가 일치하지 않습니다."),
    PASSWORD_NOT_SET(400, "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다."),

    // 숙제
    ASSIGNMENT_CLOSED(400, "마감된 숙제입니다."),

    // 단어 시험
    ATTEMPT_ALREADY_FINISHED(409, "이미 제출된 시험입니다."),
    DAY_NOT_OPENED(403, "아직 열리지 않은 학습입니다."),

    // 파일
    FILE_NOT_UPLOADED(400, "업로드가 완료되지 않은 파일입니다.");

    private final int status;
    private final String defaultMessage;

    ErrorCode(int status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public int status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
