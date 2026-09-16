package com.akshaya.shopsphere.auth;

public record UserAuthInfo(int userId, String name, String userType, String passwordHash) {
}
