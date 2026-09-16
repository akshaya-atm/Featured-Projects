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

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet("/admin/orders/*")
public class AdminOrderServlet extends HttpServlet {

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

    // GET /admin/orders -> Fetch All Orders Across All Users
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            List<Order> orders = orderService.getAllOrders();
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), orders);
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }

    // PUT /admin/orders -> Update Order Status / Payment Status
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            Map<String, Object> body = objectMapper.readValue(request.getReader(), Map.class);
            if (body == null || body.get("orderId") == null || body.get("status") == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "orderId and status are required"));
                return;
            }

            int orderId = Integer.parseInt(body.get("orderId").toString());
            OrderStatus status = OrderStatus.valueOf(body.get("status").toString());

            // Cancellation restores stock and decides PAID -> REFUNDED itself, so client-supplied paymentStatus is ignored here
            if (status == OrderStatus.CANCELLED) {
                orderService.cancelOrderByAdmin(orderId);
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Order cancelled successfully."));
                return;
            }

            PaymentStatus paymentStatus = body.get("paymentStatus") != null ? PaymentStatus.valueOf(body.get("paymentStatus").toString()) : null;

            boolean updated = orderService.updateOrderStatus(orderId, status, paymentStatus);
            if (updated) {
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Order status updated successfully"));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Order not found"));
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", e.getMessage() != null ? e.getMessage() : "Invalid status enum value"));
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }
}
