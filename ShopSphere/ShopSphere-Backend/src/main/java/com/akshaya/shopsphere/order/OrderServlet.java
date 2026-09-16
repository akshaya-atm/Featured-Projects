package com.akshaya.shopsphere.order;

import com.akshaya.shopsphere.cart.CartRepository;
import com.akshaya.shopsphere.cart.ICartRepository;
import com.akshaya.shopsphere.product.IProductRepository;
import com.akshaya.shopsphere.product.ProductRepository;
import com.akshaya.shopsphere.discount.DiscountService;
import com.akshaya.shopsphere.discount.IDiscountRepository;
import com.akshaya.shopsphere.discount.DiscountRepository;
import com.akshaya.shopsphere.product.IProductBatchRepository;
import com.akshaya.shopsphere.product.ProductBatchRepository;
import com.akshaya.shopsphere.address.IAddressRepository;
import com.akshaya.shopsphere.address.AddressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.akshaya.shopsphere.common.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.akshaya.shopsphere.discount.OrderDiscount;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet("/orders/*")
public class OrderServlet extends HttpServlet {

    private OrderService orderService;
    private ObjectMapper objectMapper;

    @Override
    public void init() throws ServletException {
        super.init();
        IOrderRepository orderRepository = new OrderRepository();
        ICartRepository cartRepository = new CartRepository();
        IProductRepository productRepository = new ProductRepository();
        IDiscountRepository discountRepository = new DiscountRepository();
        DiscountService discountService = new DiscountService(discountRepository);
        IProductBatchRepository productBatchRepository = new ProductBatchRepository();
        IAddressRepository addressRepository = new AddressRepository();
        this.orderService = new OrderService(orderRepository, cartRepository, productRepository, discountService, productBatchRepository, addressRepository);
        this.objectMapper = JsonUtil.newObjectMapper();
    }

    // GET /orders -> Get Order History for Authenticated User
    // GET /orders/eligible-discounts?subtotal=1250.00&addressId=12 -> Preview eligible order-level
    //     discounts for the checkout picker. addressId is optional -- omitting it means area/pincode discounts won't show.
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "User not authenticated"));
            return;
        }

        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/eligible-discounts")) {
                String subtotalParam = request.getParameter("subtotal");
                BigDecimal subtotal = subtotalParam != null ? new BigDecimal(subtotalParam) : BigDecimal.ZERO;
                String addressIdParam = request.getParameter("addressId");
                Integer addressId = addressIdParam != null ? Integer.parseInt(addressIdParam) : null;
                List<OrderDiscount> eligibleDiscounts = orderService.getEligibleOrderDiscounts(userId, subtotal, addressId);
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), eligibleDiscounts);
            } else {
                List<Order> orders = orderService.getUserOrders(userId);
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), orders);
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Invalid subtotal"));
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }

    // POST /orders/checkout -> Place Order for Authenticated User
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "User not authenticated"));
            return;
        }

        try {
            Integer addressId = null;
            PaymentMethod paymentMethod = PaymentMethod.CASH_ON_DELIVERY;
            Integer selectedDiscountId = null;

            if (request.getContentLength() > 0) {
                try {
                    Map<String, Object> body = objectMapper.readValue(request.getReader(), Map.class);
                    if (body != null) {
                        if (body.get("addressId") != null) {
                            addressId = Integer.parseInt(body.get("addressId").toString());
                        }
                        if (body.get("paymentMethod") != null) {
                            paymentMethod = PaymentMethod.valueOf(body.get("paymentMethod").toString());
                        }
                        if (body.get("discountId") != null) {
                            selectedDiscountId = Integer.parseInt(body.get("discountId").toString());
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            Order placedOrder = orderService.placeOrder(userId, addressId, paymentMethod, selectedDiscountId);
            response.setStatus(HttpServletResponse.SC_CREATED);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", true,
                    "message", "Order placed successfully!",
                    "order", placedOrder
            ));
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", e.getMessage()));
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }

    // PUT /orders/cancel -> Customer cancels their own order (body: {"orderId": 123}).
    // Only allowed while the order is still PLACED; enforced in OrderService.cancelOrderByUser.
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "User not authenticated"));
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.equals("/cancel")) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Not found"));
            return;
        }

        try {
            Map<String, Object> body = objectMapper.readValue(request.getReader(), Map.class);
            if (body == null || body.get("orderId") == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "orderId is required"));
                return;
            }
            int orderId = Integer.parseInt(body.get("orderId").toString());

            orderService.cancelOrderByUser(userId, orderId);
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Order cancelled successfully."));
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", e.getMessage()));
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }
}
