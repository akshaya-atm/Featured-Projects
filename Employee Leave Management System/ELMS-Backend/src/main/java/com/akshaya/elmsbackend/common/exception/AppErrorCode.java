package com.akshaya.elmsbackend.common.exception;

import org.springframework.http.HttpStatus;

public interface AppErrorCode {
    String name();
    HttpStatus getStatus();
}
