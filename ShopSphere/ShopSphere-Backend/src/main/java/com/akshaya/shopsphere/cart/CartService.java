package com.akshaya.shopsphere.cart;

import com.akshaya.shopsphere.product.IProductRepository;
import com.akshaya.shopsphere.product.Product;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class CartService {
    private final ICartRepository cartRepository;
    private final IProductRepository productRepository;

    public CartService(ICartRepository cartRepository, IProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    public boolean addItemToCart(CartItem item, int userId) throws SQLException {
        Product product = productRepository.getProductById(item.getProductId());
        if (product == null) {
            throw new IllegalArgumentException("Product not found");
        }

        int cartId = cartRepository.findCartIdByUserId(userId);
        if (cartId == 0) {
            cartId = cartRepository.createCart(userId);
        }
        if (cartId == 0) {
            return false;
        }

        // Enforce stock availability server-side too -- the frontend check is easily bypassed.
        int existingQuantity = 0;
        for (CartItem existing : cartRepository.getCartItemsByCartId(cartId)) {
            if (existing.getProductId() == item.getProductId()) {
                existingQuantity = existing.getQuantity();
                break;
            }
        }
        int requestedTotal = existingQuantity + item.getQuantity();
        if (requestedTotal > product.getTotalAvailableQuantity()) {
            throw new IllegalArgumentException(
                    "Only " + product.getTotalAvailableQuantity() + " unit(s) of '" + product.getName() + "' available.");
        }

        return cartRepository.addItemToCart(cartId, item);
    }

    public boolean removeItemFromCart(CartItem item, int userId) throws SQLException {
        int cartId = cartRepository.findCartIdByUserId(userId);
        if (cartId == 0) {
            return false;
        }
        return cartRepository.removeItemFromCart(cartId, item);
    }

    public List<CartItem> getCartItemsForUser(int userId) throws SQLException {
        int cartId = cartRepository.findCartIdByUserId(userId);
        if (cartId == 0) {
            return Collections.emptyList();
        }
        return cartRepository.getCartItemsByCartId(cartId);
    }
}
