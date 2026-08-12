package com.purut.common.error;

/**
 * 리소스 소유권 위반(IDOR 방어) 시 사용한다.
 * 존재 여부 자체를 숨겨야 하는 리소스는 NotFoundException 으로 위장하는 것도 검토한다.
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
