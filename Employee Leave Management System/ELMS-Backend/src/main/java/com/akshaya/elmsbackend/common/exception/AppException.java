package com.akshaya.elmsbackend.common.exception;

public class AppException extends RuntimeException {
    private final AppErrorCode errorCode;

    public AppException(AppErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AppErrorCode getErrorCode() {
        return errorCode;
    }
}
