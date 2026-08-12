package com.purut.infra.oauth;

import com.purut.common.error.BusinessException;
import com.purut.common.error.ErrorCode;

public class OAuth2ProfileException extends BusinessException {

    public OAuth2ProfileException(String message) {
        super(ErrorCode.INVALID_TOKEN, message);
    }
}
