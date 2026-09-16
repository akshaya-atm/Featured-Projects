package com.akshaya.shopsphere.profile;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
) {
}
