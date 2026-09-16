package com.akshaya.shopsphere.address;

import com.akshaya.shopsphere.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AddressRepository implements IAddressRepository {

    @Override
    public List<Address> getAddressesByUserId(int userId) throws SQLException {
        List<Address> addresses = new ArrayList<>();
        // Default address first, then most recently added -- matches checkout UI's pre-selection.
        String sql = "SELECT address_id, user_id, house_no, street, landmark, city, state, pincode, is_default " +
                "FROM addresses WHERE user_id = ? ORDER BY is_default DESC, address_id DESC";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    addresses.add(mapRow(rs));
                }
            }
        }
        return addresses;
    }

    @Override
    public Address getAddressById(int addressId) throws SQLException {
        String sql = "SELECT address_id, user_id, house_no, street, landmark, city, state, pincode, is_default " +
                "FROM addresses WHERE address_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, addressId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public int addAddress(Address address) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // A customer's first address is always their default, regardless of the request.
                boolean isFirstAddress = countAddressesForUser(connection, address.getUserId()) == 0;
                boolean makeDefault = isFirstAddress || address.isDefaultAddress();

                if (makeDefault) {
                    clearDefaultForUser(connection, address.getUserId());
                }

                String sql = "INSERT INTO addresses (user_id, house_no, street, landmark, city, state, pincode, is_default) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                int newAddressId = 0;
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, address.getUserId());
                    statement.setString(2, address.getHouseNo());
                    statement.setString(3, address.getStreet());
                    statement.setString(4, address.getLandmark());
                    statement.setString(5, address.getCity());
                    statement.setString(6, address.getState());
                    statement.setString(7, address.getPincode());
                    statement.setBoolean(8, makeDefault);
                    statement.executeUpdate();

                    try (ResultSet rs = statement.getGeneratedKeys()) {
                        if (rs.next()) {
                            newAddressId = rs.getInt(1);
                        }
                    }
                }

                connection.commit();
                return newAddressId;
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException sqlEx) {
                    throw sqlEx;
                }
                throw new SQLException(e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean updateAddress(Address address) throws SQLException {
        String sql = "UPDATE addresses SET house_no = ?, street = ?, landmark = ?, city = ?, state = ?, pincode = ? " +
                "WHERE address_id = ? AND user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, address.getHouseNo());
            statement.setString(2, address.getStreet());
            statement.setString(3, address.getLandmark());
            statement.setString(4, address.getCity());
            statement.setString(5, address.getState());
            statement.setString(6, address.getPincode());
            statement.setInt(7, address.getAddressId());
            statement.setInt(8, address.getUserId());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deleteAddress(int addressId, int userId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean wasDefault = false;
                String checkSql = "SELECT is_default FROM addresses WHERE address_id = ? AND user_id = ?";
                try (PreparedStatement checkStatement = connection.prepareStatement(checkSql)) {
                    checkStatement.setInt(1, addressId);
                    checkStatement.setInt(2, userId);
                    try (ResultSet rs = checkStatement.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return false; // not found, or doesn't belong to this user
                        }
                        wasDefault = rs.getBoolean("is_default");
                    }
                }

                boolean deleted;
                String deleteSql = "DELETE FROM addresses WHERE address_id = ? AND user_id = ?";
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                    deleteStatement.setInt(1, addressId);
                    deleteStatement.setInt(2, userId);
                    deleted = deleteStatement.executeUpdate() > 0;
                }

                // If the deleted address was the default, promote the most recently added one.
                if (deleted && wasDefault) {
                    String promoteSql = "UPDATE addresses SET is_default = TRUE WHERE address_id = (" +
                            "SELECT address_id FROM addresses WHERE user_id = ? ORDER BY address_id DESC LIMIT 1)";
                    try (PreparedStatement promoteStatement = connection.prepareStatement(promoteSql)) {
                        promoteStatement.setInt(1, userId);
                        promoteStatement.executeUpdate();
                    }
                }

                connection.commit();
                return deleted;
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException sqlEx) {
                    throw sqlEx;
                }
                throw new SQLException(e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean setDefaultAddress(int addressId, int userId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                clearDefaultForUser(connection, userId);

                boolean updated;
                String sql = "UPDATE addresses SET is_default = TRUE WHERE address_id = ? AND user_id = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, addressId);
                    statement.setInt(2, userId);
                    updated = statement.executeUpdate() > 0;
                }

                connection.commit();
                return updated;
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException sqlEx) {
                    throw sqlEx;
                }
                throw new SQLException(e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void clearDefaultForUser(Connection connection, int userId) throws SQLException {
        String sql = "UPDATE addresses SET is_default = FALSE WHERE user_id = ? AND is_default = TRUE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.executeUpdate();
        }
    }

    private int countAddressesForUser(Connection connection, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM addresses WHERE user_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Address mapRow(ResultSet rs) throws SQLException {
        return new Address(
                rs.getInt("address_id"),
                rs.getInt("user_id"),
                rs.getString("house_no"),
                rs.getString("street"),
                rs.getString("landmark"),
                rs.getString("city"),
                rs.getString("state"),
                rs.getString("pincode"),
                rs.getBoolean("is_default")
        );
    }
}
