package com.akshaya.elmsbackend.employee;

import com.akshaya.elmsbackend.common.exception.AppErrorCode;
import org.springframework.http.HttpStatus;

public enum EmployeeErrorCode implements AppErrorCode {
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND),
    EMPLOYEE_INACTIVE(HttpStatus.FORBIDDEN);

    private final HttpStatus status;

    EmployeeErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}
