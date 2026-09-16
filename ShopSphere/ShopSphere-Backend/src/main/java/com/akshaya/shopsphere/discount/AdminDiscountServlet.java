package com.akshaya.shopsphere.discount;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.akshaya.shopsphere.common.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet("/admin/discounts/*")
public class AdminDiscountServlet extends HttpServlet {

    private DiscountService discountService;
    private ObjectMapper objectMapper;

    @Override
    public void init() throws ServletException {
        super.init();
        IDiscountRepository discountRepository = new DiscountRepository();
        this.discountService = new DiscountService(discountRepository);
        this.objectMapper = JsonUtil.newObjectMapper();
    }

    // GET /admin/discounts -- returns ALL discounts, including expired/upcoming ones (unlike the public endpoint)
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            List<Discount> discounts = discountService.getAllDiscounts();
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), discounts);
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }

    // POST /admin/discounts (Admin creates a discount)
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            Map<String, Object> body = objectMapper.readValue(request.getReader(), Map.class);
            
            Discount discount = new Discount();
            discount.setName((String) body.get("name"));
            discount.setDiscountType((String) body.get("discountType"));
            discount.setDiscountValue(new BigDecimal(body.get("discountValue").toString()));
            discount.setTargetScope((String) body.get("targetScope"));
            
            if (body.get("minOrderAmount") != null) {
                discount.setMinOrderAmount(new BigDecimal(body.get("minOrderAmount").toString()));
            }
            discount.setTargetPincode((String) body.get("targetPincode"));
            if (body.get("targetUserId") != null) {
                discount.setTargetUserId(Integer.parseInt(body.get("targetUserId").toString()));
            }
            // Key is "birthdayOnly" to match Jackson's property name for isBirthdayOnly()/setBirthdayOnly()
            if (body.get("birthdayOnly") != null) {
                discount.setBirthdayOnly(Boolean.parseBoolean(body.get("birthdayOnly").toString()));
            }

            discount.setValidFrom(java.time.LocalDateTime.parse(body.get("validFrom").toString()));
            discount.setValidUntil(java.time.LocalDateTime.parse(body.get("validUntil").toString()));

            Integer productId = body.get("productId") != null ? Integer.parseInt(body.get("productId").toString()) : null;

            int discountId = discountService.addDiscount(discount, productId);

            if (discountId > 0) {
                response.setStatus(HttpServletResponse.SC_CREATED);
                objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Discount created successfully", "discountId", discountId));
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Failed to create discount"));
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Server error"));
        }
    }

    // PUT /admin/discounts (Admin updates a discount)
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            Discount discount = objectMapper.readValue(request.getReader(), Discount.class);
            boolean updated = discountService.updateDiscount(discount);

            if (updated) {
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Discount updated successfully"));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Discount not found"));
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", e.getMessage()));
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }

    // DELETE /admin/discounts?id=1 (Admin deletes a discount)
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String idParam = request.getParameter("id");
        if (idParam == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Discount ID is required"));
            return;
        }

        try {
            int discountId = Integer.parseInt(idParam);
            boolean deleted = discountService.deleteDiscount(discountId);

            if (deleted) {
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Discount deleted successfully"));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Discount not found"));
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Invalid discount ID format"));
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
