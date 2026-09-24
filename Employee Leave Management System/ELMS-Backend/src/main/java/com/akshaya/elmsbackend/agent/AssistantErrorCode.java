package com.akshaya.elmsbackend.agent;

import com.akshaya.elmsbackend.common.exception.AppErrorCode;
import org.springframework.http.HttpStatus;

public enum AssistantErrorCode implements AppErrorCode {
    EMPTY_MESSAGE(HttpStatus.BAD_REQUEST),
    MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONVERSATION_ACCESS_DENIED(HttpStatus.FORBIDDEN),
    ASSISTANT_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    MODEL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    MODEL_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    INVALID_MODEL_RESPONSE(HttpStatus.BAD_GATEWAY),
    TOOL_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    AssistantErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}
