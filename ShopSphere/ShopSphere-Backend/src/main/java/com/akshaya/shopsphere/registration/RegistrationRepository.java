package com.akshaya.shopsphere.registration;

import com.akshaya.shopsphere.common.IPasswordService;
import com.akshaya.shopsphere.db.DatabaseConnection;
import com.akshaya.shopsphere.user.UserType;

import java.sql.*;

public class RegistrationRepository implements IRegistrationRepository {
    private IPasswordService passwordService;

    public RegistrationRepository(IPasswordService passwordService) {
        this.passwordService = passwordService;
    }

    @Override
    public boolean registerUser(RegistrationRequest user) throws SQLException {

        String userQuery = """
        INSERT INTO users(name, email, contact, birthday, user_type)
        VALUES (?, ?, ?, ?, ?)
        """;

        String authQuery = """
        INSERT INTO auth_info(user_id, password_hash)
        VALUES (?, ?)
        """;

        Connection connection = DatabaseConnection.getConnection();

        try {
            connection.setAutoCommit(false);

            // 1. Insert user and get generated user_id
            try (PreparedStatement userStatement =
                         connection.prepareStatement(
                                 userQuery,
                                 Statement.RETURN_GENERATED_KEYS)) {

                userStatement.setString(1, user.name());
                userStatement.setString(2, user.email());
                userStatement.setString(3, user.contact());
                userStatement.setDate(4, Date.valueOf(user.birthday()));
                userStatement.setString(5, UserType.CUSTOMER.toString());

                userStatement.executeUpdate();

                try (ResultSet resultSet = userStatement.getGeneratedKeys()) {

                    if (!resultSet.next()) {
                        connection.rollback();
                        return false;
                    }

                    int userId = resultSet.getInt(1);

                    // 2. Hash password
                    String hashPassword =
                            passwordService.hashPassword(user.password());

                    // 3. Insert credentials
                    try (PreparedStatement authStatement =
                                 connection.prepareStatement(authQuery)) {

                        authStatement.setInt(1, userId);
                        authStatement.setString(2, hashPassword);

                        authStatement.executeUpdate();
                    }
                }
            }

            // Both inserts succeeded
            connection.commit();
            return true;

        } catch (Exception e) {
            // Something failed → undo both inserts
            connection.rollback();
            if (e instanceof SQLException sqlEx) {
                throw sqlEx;
            }
            throw new SQLException(e);
        } finally {
            connection.setAutoCommit(true);
            connection.close();
        }
    }
}
