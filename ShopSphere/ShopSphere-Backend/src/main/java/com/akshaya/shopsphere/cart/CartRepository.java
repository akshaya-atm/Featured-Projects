package com.akshaya.shopsphere.cart;

import com.akshaya.shopsphere.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CartRepository implements ICartRepository {

    @Override
    public int findCartIdByUserId(int userId) throws SQLException {
        String query = "SELECT cart_id FROM carts WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("cart_id");
                }
            }
        }
        return 0;
    }

    @Override
    public int createCart(int userId) throws SQLException {
        String query = "INSERT INTO carts (user_id) VALUES (?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, userId);
            statement.executeUpdate();
            try (ResultSet resultSet = statement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public boolean addItemToCart(int cartId, CartItem item) throws SQLException {
        String checkQuery = "SELECT cart_item_id, quantity FROM cart_items WHERE cart_id = ? AND product_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setInt(1, cartId);
            checkStmt.setInt(2, item.getProductId());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    String updateQuery = "UPDATE cart_items SET quantity = quantity + ? WHERE cart_id = ? AND product_id = ?";
                    try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
                        updateStmt.setInt(1, item.getQuantity());
                        updateStmt.setInt(2, cartId);
                        updateStmt.setInt(3, item.getProductId());
                        return updateStmt.executeUpdate() > 0;
                    }
                } else {
                    String insertQuery = "INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, ?)";
                    try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
                        insertStmt.setInt(1, cartId);
                        insertStmt.setInt(2, item.getProductId());
                        insertStmt.setInt(3, item.getQuantity());
                        return insertStmt.executeUpdate() > 0;
                    }
                }
            }
        }
    }

    @Override
    public boolean removeItemFromCart(int cartId, CartItem item) throws SQLException {
        String selectQuery = "SELECT quantity FROM cart_items WHERE cart_id = ? AND product_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement selectStmt = connection.prepareStatement(selectQuery)) {
            selectStmt.setInt(1, cartId);
            selectStmt.setInt(2, item.getProductId());
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (!rs.next()) {
                    return false; // Item not found in cart
                }
                int currentQuantity = rs.getInt("quantity");
                int removeQuantity = item.getQuantity();

                if (removeQuantity > 0 && currentQuantity - removeQuantity > 0) {
                    String updateQuery = "UPDATE cart_items SET quantity = quantity - ? WHERE cart_id = ? AND product_id = ?";
                    try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
                        updateStmt.setInt(1, removeQuantity);
                        updateStmt.setInt(2, cartId);
                        updateStmt.setInt(3, item.getProductId());
                        return updateStmt.executeUpdate() > 0;
                    }
                } else {
                    String deleteQuery = "DELETE FROM cart_items WHERE cart_id = ? AND product_id = ?";
                    try (PreparedStatement deleteStmt = connection.prepareStatement(deleteQuery)) {
                        deleteStmt.setInt(1, cartId);
                        deleteStmt.setInt(2, item.getProductId());
                        return deleteStmt.executeUpdate() > 0;
                    }
                }
            }
        }
    }

    @Override
    public List<CartItem> getCartItemsByCartId(int cartId) throws SQLException {
        List<CartItem> items = new ArrayList<>();
        String sql = "SELECT cart_item_id, product_id, quantity FROM cart_items WHERE cart_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, cartId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new CartItem(
                            rs.getInt("cart_item_id"),
                            rs.getInt("product_id"),
                            rs.getInt("quantity")
                    ));
                }
            }
        }
        return items;
    }

    @Override
    public void clearCart(int cartId) throws SQLException {
        String sql = "DELETE FROM cart_items WHERE cart_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, cartId);
            statement.executeUpdate();
        }
    }
}
