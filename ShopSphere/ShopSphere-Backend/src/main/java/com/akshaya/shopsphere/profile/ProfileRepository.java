package com.akshaya.shopsphere.profile;

import com.akshaya.shopsphere.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ProfileRepository implements IProfileRepository {

    @Override
    public ProfileResponse getProfile(int userId) throws SQLException {
        String query = "SELECT name, email, contact, birthday FROM users WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ProfileResponse(
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("contact"),
                            rs.getDate("birthday") != null ? rs.getDate("birthday").toLocalDate() : null
                    );
                }
            }
        }
        return null;
    }

    @Override
    public boolean updateProfile(int userId, String name, String contact) throws SQLException {
        String query = "UPDATE users SET name = ?, contact = ? WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, contact);
            stmt.setInt(3, userId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public String getPasswordHash(int userId) throws SQLException {
        String query = "SELECT password_hash FROM auth_info WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password_hash");
                }
            }
        }
        return null;
    }

    @Override
    public boolean updatePassword(int userId, String newPasswordHash) throws SQLException {
        String query = "UPDATE auth_info SET password_hash = ? WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, newPasswordHash);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        }
    }
}
