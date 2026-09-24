package com.akshaya.elmsbackend.auth;

public record LoginRequest(
        String employeeId,
        String password
) {
}