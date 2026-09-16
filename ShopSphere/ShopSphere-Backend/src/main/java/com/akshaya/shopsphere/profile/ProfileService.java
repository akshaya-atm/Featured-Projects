package com.akshaya.shopsphere.profile;

import com.akshaya.shopsphere.common.IPasswordService;
import com.akshaya.shopsphere.common.UserValidationUtil;

import java.sql.SQLException;

public class ProfileService {

    private final IProfileRepository profileRepository;
    private final IPasswordService passwordService;

    public ProfileService(IProfileRepository profileRepository, IPasswordService passwordService) {
        this.profileRepository = profileRepository;
        this.passwordService = passwordService;
    }

    public ProfileResponse getProfile(int userId) throws SQLException {
        ProfileResponse profile = profileRepository.getProfile(userId);
        if (profile == null) {
            throw new IllegalArgumentException("User profile not found");
        }
        return profile;
    }

    public boolean updateProfile(int userId, UpdateProfileRequest request) throws SQLException {
        UserValidationUtil.validateName(request.name());
        UserValidationUtil.validateContact(request.contact());
        
        return profileRepository.updateProfile(userId, request.name(), request.contact());
    }

    public boolean changePassword(int userId, ChangePasswordRequest request) throws SQLException {
        if (request.currentPassword() == null || request.newPassword() == null) {
            throw new IllegalArgumentException("Current password and new password are required");
        }
        
        String currentHash = profileRepository.getPasswordHash(userId);
        if (currentHash == null || !passwordService.checkPassword(request.currentPassword(), currentHash)) {
            throw new IllegalArgumentException("Incorrect current password");
        }
        
        UserValidationUtil.validatePassword(request.newPassword());
        
        try {
            String newHash = passwordService.hashPassword(request.newPassword());
            return profileRepository.updatePassword(userId, newHash);
        } catch (java.security.NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
}
