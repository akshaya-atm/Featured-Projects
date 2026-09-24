package com.akshaya.elmsbackend.rag;

import com.akshaya.elmsbackend.common.exception.AppErrorCode;
import org.springframework.http.HttpStatus;

public enum RagErrorCode implements AppErrorCode {

    EMPTY_POLICY_QUERY(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    RagErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}
