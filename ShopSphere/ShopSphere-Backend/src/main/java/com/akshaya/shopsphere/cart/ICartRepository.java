package com.akshaya.shopsphere.cart;

import java.sql.SQLException;
import java.util.List;

public interface ICartRepository {
    int findCartIdByUserId(int userId) throws SQLException;
    int createCart(int userId) throws SQLException;
    boolean addItemToCart(int cartId, CartItem item) throws SQLException;
    boolean removeItemFromCart(int cartId, CartItem item) throws SQLException;
    List<CartItem> getCartItemsByCartId(int cartId) throws SQLException;
    void clearCart(int cartId) throws SQLException;
}
