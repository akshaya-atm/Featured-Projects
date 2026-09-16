package com.akshaya.shopsphere.profile;

import java.sql.SQLException;

public interface IProfileRepository {
    ProfileResponse getProfile(int userId) throws SQLException;
    boolean updateProfile(int userId, String name, String contact) throws SQLException;
    String getPasswordHash(int userId) throws SQLException;
    boolean updatePassword(int userId, String newPasswordHash) throws SQLException;
}
