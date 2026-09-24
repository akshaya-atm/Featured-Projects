package com.akshaya.elmsbackend.auth;

import com.akshaya.elmsbackend.common.exception.AppErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements AppErrorCode {
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED),
    TOKEN_MISSING(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    GENERAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    AuthErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}
