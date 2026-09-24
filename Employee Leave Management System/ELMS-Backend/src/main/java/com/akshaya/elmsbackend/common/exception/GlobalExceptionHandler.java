package com.akshaya.elmsbackend.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorResponse> handleAppException(AppException ex) {
        AppErrorCode errorCode = ex.getErrorCode();
        ApiErrorResponse response = new ApiErrorResponse(errorCode.name(), ex.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }
}
