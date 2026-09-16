package com.akshaya.shopsphere.auth;

import com.akshaya.shopsphere.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginRepository implements ILoginRepository {
    @Override
    public String getPasswordHashforEmail(String email) throws SQLException {
        return getAuthInfoForEmail(email).passwordHash();
    }

    @Override
    public UserAuthInfo getAuthInfoForEmail(String email) throws SQLException {
        Connection connection = DatabaseConnection.getConnection();
        String query = """
                SELECT u.user_id, u.name, u.user_type, a.password_hash
                FROM users u
                JOIN auth_info a
                ON u.user_id=a.user_id
                WHERE u.email = ?        
                """;
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Invalid email or password");
                }
                int userId = resultSet.getInt("user_id");
                String name = resultSet.getString("name");
                String userType = resultSet.getString("user_type");
                String passwordHash = resultSet.getString("password_hash");
                return new UserAuthInfo(userId, name, userType, passwordHash);
            }
        }
    }
}
