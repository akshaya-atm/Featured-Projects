package com.akshaya.shopsphere.discount;

import com.akshaya.shopsphere.db.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DiscountRepository implements IDiscountRepository {

    @Override
    public int addDiscount(Discount discount) throws SQLException {
        String sql = """
            INSERT INTO discounts (name, discount_type, discount_value, target_scope, min_order_amount, target_pincode, target_user_id, is_birthday_only, valid_from, valid_until)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            preparedStatement.setString(1, discount.getName());
            preparedStatement.setString(2, discount.getDiscountType());
            preparedStatement.setBigDecimal(3, discount.getDiscountValue());
            preparedStatement.setString(4, discount.getTargetScope());

            if (discount.getMinOrderAmount() != null) {
                preparedStatement.setBigDecimal(5, discount.getMinOrderAmount());
            } else {
                preparedStatement.setNull(5, Types.DECIMAL);
            }

            preparedStatement.setString(6, discount.getTargetPincode());

            if (discount.getTargetUserId() != null) {
                preparedStatement.setInt(7, discount.getTargetUserId());
            } else {
                preparedStatement.setNull(7, Types.INTEGER);
            }

            preparedStatement.setBoolean(8, discount.isBirthdayOnly());
            preparedStatement.setTimestamp(9, Timestamp.valueOf(discount.getValidFrom()));
            preparedStatement.setTimestamp(10, Timestamp.valueOf(discount.getValidUntil()));

            preparedStatement.executeUpdate();

            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }
        return -1;
    }

    @Override
    public boolean linkProductDiscount(int productId, int discountId) throws SQLException {
        String sql = "INSERT INTO product_discounts (product_id, discount_id) VALUES (?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, productId);
            preparedStatement.setInt(2, discountId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    @Override
    public void relinkProductDiscount(int discountId, Integer productId) throws SQLException {
        String deleteSql = "DELETE FROM product_discounts WHERE discount_id = ?";
        String insertSql = "INSERT INTO product_discounts (product_id, discount_id) VALUES (?, ?)";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                    deleteStatement.setInt(1, discountId);
                    deleteStatement.executeUpdate();
                }
                if (productId != null && productId > 0) {
                    try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                        insertStatement.setInt(1, productId);
                        insertStatement.setInt(2, discountId);
                        insertStatement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw new SQLException(e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean updateDiscount(Discount discount) throws SQLException {
        String sql = """
            UPDATE discounts
            SET name = ?, discount_type = ?, discount_value = ?, target_scope = ?, min_order_amount = ?, target_pincode = ?, target_user_id = ?, is_birthday_only = ?, valid_from = ?, valid_until = ?
            WHERE discount_id = ?
            """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setString(1, discount.getName());
            preparedStatement.setString(2, discount.getDiscountType());
            preparedStatement.setBigDecimal(3, discount.getDiscountValue());
            preparedStatement.setString(4, discount.getTargetScope());

            if (discount.getMinOrderAmount() != null) {
                preparedStatement.setBigDecimal(5, discount.getMinOrderAmount());
            } else {
                preparedStatement.setNull(5, Types.DECIMAL);
            }

            preparedStatement.setString(6, discount.getTargetPincode());

            if (discount.getTargetUserId() != null) {
                preparedStatement.setInt(7, discount.getTargetUserId());
            } else {
                preparedStatement.setNull(7, Types.INTEGER);
            }

            preparedStatement.setBoolean(8, discount.isBirthdayOnly());
            preparedStatement.setTimestamp(9, Timestamp.valueOf(discount.getValidFrom()));
            preparedStatement.setTimestamp(10, Timestamp.valueOf(discount.getValidUntil()));
            preparedStatement.setInt(11, discount.getDiscountId());

            return preparedStatement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deleteDiscount(int discountId) throws SQLException {
        String sql = "DELETE FROM discounts WHERE discount_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, discountId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    @Override
    public List<Discount> getCurrentlyActiveDiscounts() throws SQLException {
        List<Discount> list = new ArrayList<>();
        String sql = "SELECT * FROM discounts WHERE CURRENT_TIMESTAMP BETWEEN valid_from AND valid_until ORDER BY discount_id DESC";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet rs = preparedStatement.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToDiscount(rs));
            }
        }
        return list;
    }

    @Override
    public List<Discount> getAllDiscounts() throws SQLException {
        List<Discount> list = new ArrayList<>();
        String sql = """
            SELECT d.*, pd.product_id AS linked_product_id
            FROM discounts d
            LEFT JOIN product_discounts pd ON pd.discount_id = d.discount_id
            ORDER BY d.discount_id DESC
            """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet rs = preparedStatement.executeQuery()) {
            while (rs.next()) {
                Discount discount = mapResultSetToDiscount(rs);
                Object linkedProductId = rs.getObject("linked_product_id");
                if (linkedProductId != null) {
                    discount.setProductId((Integer) linkedProductId);
                }
                list.add(discount);
            }
        }
        return list;
    }

    @Override
    public Discount getDiscountById(int discountId) throws SQLException {
        String sql = "SELECT * FROM discounts WHERE discount_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, discountId);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToDiscount(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<Discount> getActiveProductDiscounts(int productId) throws SQLException {
        List<Discount> list = new ArrayList<>();
        String sql = """
            SELECT d.* FROM discounts d
            JOIN product_discounts pd ON d.discount_id = pd.discount_id
            WHERE pd.product_id = ? AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until
            """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, productId);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToDiscount(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Discount> getActiveOrderDiscounts() throws SQLException {
        List<Discount> list = new ArrayList<>();
        String sql = "SELECT * FROM discounts WHERE target_scope != 'PRODUCT' AND CURRENT_TIMESTAMP BETWEEN valid_from AND valid_until";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet rs = preparedStatement.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToDiscount(rs));
            }
        }
        return list;
    }

    private Discount mapResultSetToDiscount(ResultSet rs) throws SQLException {
        return new Discount(
                rs.getInt("discount_id"),
                rs.getString("name"),
                rs.getString("discount_type"),
                rs.getBigDecimal("discount_value"),
                rs.getString("target_scope"),
                rs.getBigDecimal("min_order_amount"),
                rs.getString("target_pincode"),
                rs.getObject("target_user_id") != null ? rs.getInt("target_user_id") : null,
                rs.getBoolean("is_birthday_only"),
                rs.getTimestamp("valid_from").toLocalDateTime(),
                rs.getTimestamp("valid_until").toLocalDateTime()
        );
    }
}
