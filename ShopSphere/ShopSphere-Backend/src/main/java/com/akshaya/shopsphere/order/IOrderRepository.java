package com.akshaya.shopsphere.order;

import java.sql.SQLException;
import java.util.List;

public interface IOrderRepository {
    int createOrder(Order order) throws SQLException;
    void createOrderItems(int orderId, List<OrderItem> items) throws SQLException;
    List<Order> getOrdersByUserId(int userId) throws SQLException;
    List<Order> getAllOrders() throws SQLException;
    Order getOrderById(int orderId) throws SQLException;
    boolean updateOrderStatus(int orderId, OrderStatus status, PaymentStatus paymentStatus) throws SQLException;
    UserOrderContext getUserOrderContext(int userId) throws SQLException;
}
