package com.akshaya.shopsphere.order;

import com.akshaya.shopsphere.db.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderRepository implements IOrderRepository {

    @Override
    public int createOrder(Order order) throws SQLException {
        String sql = "INSERT INTO orders (user_id, address_id, total_amount, discount_amount, final_amount, status, payment_status, payment_method) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, order.getUserId());
            if (order.getAddressId() != null) {
                statement.setInt(2, order.getAddressId());
            } else {
                statement.setNull(2, Types.INTEGER);
            }
            statement.setBigDecimal(3, order.getTotalAmount());
            statement.setBigDecimal(4, order.getDiscountAmount());
            statement.setBigDecimal(5, order.getFinalAmount());
            statement.setString(6, order.getStatus() != null ? order.getStatus().name() : OrderStatus.PLACED.name());
            statement.setString(7, order.getPaymentStatus() != null ? order.getPaymentStatus().name() : PaymentStatus.PAID.name());
            statement.setString(8, order.getPaymentMethod() != null ? order.getPaymentMethod().name() : PaymentMethod.CASH_ON_DELIVERY.name());
            statement.executeUpdate();

            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public void createOrderItems(int orderId, List<OrderItem> items) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, product_id, quantity, price_per_unit) VALUES (?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (OrderItem item : items) {
                statement.setInt(1, orderId);
                statement.setInt(2, item.getProductId());
                statement.setInt(3, item.getQuantity());
                statement.setBigDecimal(4, item.getPricePerUnit());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    public List<Order> getOrdersByUserId(int userId) throws SQLException {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT order_id, user_id, address_id, total_amount, discount_amount, final_amount, status, payment_status, payment_method, created_at FROM orders WHERE user_id = ? ORDER BY order_id DESC";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapResultSetToOrder(connection, rs));
                }
            }
        }
        return orders;
    }

    @Override
    public List<Order> getAllOrders() throws SQLException {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT order_id, user_id, address_id, total_amount, discount_amount, final_amount, status, payment_status, payment_method, created_at FROM orders ORDER BY order_id DESC";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                orders.add(mapResultSetToOrder(connection, rs));
            }
        }
        return orders;
    }

    @Override
    public Order getOrderById(int orderId) throws SQLException {
        String sql = "SELECT order_id, user_id, address_id, total_amount, discount_amount, final_amount, status, payment_status, payment_method, created_at FROM orders WHERE order_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToOrder(connection, rs);
                }
            }
        }
        return null;
    }

    @Override
    public boolean updateOrderStatus(int orderId, OrderStatus status, PaymentStatus paymentStatus) throws SQLException {
        String sql = "UPDATE orders SET status = ?, payment_status = COALESCE(?, payment_status) WHERE order_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            if (paymentStatus != null) {
                statement.setString(2, paymentStatus.name());
            } else {
                statement.setNull(2, Types.VARCHAR);
            }
            statement.setInt(3, orderId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public UserOrderContext getUserOrderContext(int userId) throws SQLException {
        String sql = "SELECT pincode, birthday FROM users WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String pincode = rs.getString("pincode");
                    Date birthday = rs.getDate("birthday");
                    return new UserOrderContext(pincode, birthday != null ? birthday.toLocalDate() : null);
                }
            }
        }
        return new UserOrderContext(null, null);
    }

    private Order mapResultSetToOrder(Connection connection, ResultSet rs) throws SQLException {
        int orderId = rs.getInt("order_id");
        List<OrderItem> items = getOrderItemsByOrderId(connection, orderId);

        String statusStr = rs.getString("status");
        String paymentStatusStr = rs.getString("payment_status");
        String paymentMethodStr = rs.getString("payment_method");

        OrderStatus status = statusStr != null ? OrderStatus.valueOf(statusStr) : OrderStatus.PLACED;
        PaymentStatus paymentStatus = paymentStatusStr != null ? PaymentStatus.valueOf(paymentStatusStr) : PaymentStatus.PAID;
        PaymentMethod paymentMethod = paymentMethodStr != null ? PaymentMethod.valueOf(paymentMethodStr) : PaymentMethod.CASH_ON_DELIVERY;

        return new Order(
                orderId,
                rs.getInt("user_id"),
                (Integer) rs.getObject("address_id"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("final_amount"),
                status,
                paymentStatus,
                paymentMethod,
                rs.getTimestamp("created_at").toLocalDateTime(),
                items
        );
    }

    private List<OrderItem> getOrderItemsByOrderId(Connection connection, int orderId) throws SQLException {
        List<OrderItem> list = new ArrayList<>();
        String sql = """
            SELECT oi.order_item_id, oi.order_id, oi.product_id, p.name AS product_name, oi.quantity, oi.price_per_unit
            FROM order_items oi
            JOIN products p ON oi.product_id = p.product_id
            WHERE oi.order_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new OrderItem(
                            rs.getInt("order_item_id"),
                            rs.getInt("order_id"),
                            rs.getInt("product_id"),
                            rs.getString("product_name"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("price_per_unit")
                    ));
                }
            }
        }
        return list;
    }
}
