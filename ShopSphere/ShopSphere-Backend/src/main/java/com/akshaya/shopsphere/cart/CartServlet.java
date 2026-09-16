package com.akshaya.shopsphere.cart;

import com.akshaya.shopsphere.product.IProductRepository;
import com.akshaya.shopsphere.product.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.akshaya.shopsphere.common.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@WebServlet("/cart")
public class CartServlet extends HttpServlet {
    private ObjectMapper mapper;
    private CartService cartService;

    @Override
    public void init() throws ServletException {
        super.init();
        this.mapper = JsonUtil.newObjectMapper();
        ICartRepository cartRepository = new CartRepository();
        IProductRepository productRepository = new ProductRepository();
        this.cartService = new CartService(cartRepository, productRepository);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) req.getAttribute("userId");
        if (userId == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            mapper.writeValue(resp.getWriter(), Map.of("success", false, "message", "User not authenticated"));
            return;
        }

        try {
            List<CartItem> cartItems = cartService.getCartItemsForUser(userId);
            resp.setStatus(HttpServletResponse.SC_OK);
            mapper.writeValue(resp.getWriter(), cartItems);
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            mapper.writeValue(resp.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) req.getAttribute("userId");
        if (userId == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            mapper.writeValue(resp.getWriter(), Map.of("success", false, "message", "User not authenticated"));
            return;
        }

        CartItem cartItem = mapper.readValue(req.getReader(), CartItem.class);

        try {
            boolean success = cartService.addItemToCart(cartItem, userId);
            if (success) {
                resp.setStatus(HttpServletResponse.SC_OK);
                mapper.writeValue(resp.getWriter(), Map.of("success", true, "message", "Item added to cart"));
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                mapper.writeValue(resp.getWriter(), Map.of("success", false, "message", "Failed to add item to cart"));
            }
        } catch (IllegalArgumentException e) {
            // Message is already customer-friendly (from CartService); surface as-is with 400.
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            mapper.writeValue(resp.getWriter(), Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            mapper.writeValue(resp.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) req.getAttribute("userId");
        if (userId == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            mapper.writeValue(resp.getWriter(), Map.of("success", false, "message", "User not authenticated"));
            return;
        }

        CartItem cartItem = mapper.readValue(req.getReader(), CartItem.class);

        try {
            boolean success = cartService.removeItemFromCart(cartItem, userId);
            if (success) {
                resp.setStatus(HttpServletResponse.SC_OK);
                mapper.writeValue(resp.getWriter(), Map.of("success", true, "message", "Item removed from cart"));
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                mapper.writeValue(resp.getWriter(), Map.of("success", false, "message", "Failed to remove item from cart"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            mapper.writeValue(resp.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }
}
