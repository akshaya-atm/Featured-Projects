package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.common.exception.AppErrorCode;
import org.springframework.http.HttpStatus;

public enum LeaveErrorCode implements AppErrorCode {
    INVALID_LEAVE_TYPE(HttpStatus.BAD_REQUEST),
    INVALID_LEAVE_DATES(HttpStatus.BAD_REQUEST),
    CASUAL_DURATION_EXCEEDED(HttpStatus.BAD_REQUEST),
    LEAVE_DATE_IN_PAST(HttpStatus.BAD_REQUEST),
    NO_WORKING_DAYS(HttpStatus.BAD_REQUEST),
    OVERLAPPING_REQUEST(HttpStatus.CONFLICT),
    APPROVER_UNAVAILABLE(HttpStatus.CONFLICT),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT),
    INVALID_REASON_LENGTH(HttpStatus.BAD_REQUEST),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND),
    NOT_YOUR_TEAM(HttpStatus.FORBIDDEN),
    ALREADY_DECIDED(HttpStatus.CONFLICT),
    NO_PENDING_APPROVAL(HttpStatus.CONFLICT),
    STAGE_NOT_ASSIGNED(HttpStatus.FORBIDDEN),
    CALENDAR_MISMATCH(HttpStatus.CONFLICT),
    UNSUPPORTED_YEAR(HttpStatus.CONFLICT),
    ALLOCATION_MISSING(HttpStatus.CONFLICT),
    RESERVATION_INSUFFICIENT(HttpStatus.CONFLICT),
    INVALID_YEAR(HttpStatus.BAD_REQUEST),
    NO_DIRECT_REPORTS(HttpStatus.FORBIDDEN),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST),
    UPLOAD_NOT_ALLOWED(HttpStatus.CONFLICT),
    CERTIFICATE_NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus status;

    LeaveErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}
