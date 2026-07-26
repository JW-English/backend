package com.jungwoon.infra.oauth;

import com.jungwoon.common.error.BusinessException;
import com.jungwoon.common.error.ErrorCode;

public class OAuth2ProfileException extends BusinessException {

    public OAuth2ProfileException(String message) {
        super(ErrorCode.INVALID_TOKEN, message);
    }
}
