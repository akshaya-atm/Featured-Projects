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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@WebServlet("/discounts/*")
public class DiscountServlet extends HttpServlet {

    private DiscountService discountService;
    private ObjectMapper objectMapper;

    @Override
    public void init() throws ServletException {
        super.init();
        IDiscountRepository discountRepository = new DiscountRepository();
        this.discountService = new DiscountService(discountRepository);
        this.objectMapper = JsonUtil.newObjectMapper();
    }

    // GET /discounts OR GET /discounts/evaluate?subtotal=1200&pincode=600001 (Public / Checkout)
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/evaluate")) {
                String subtotalParam = request.getParameter("subtotal");
                String pincode = request.getParameter("pincode");
                String birthdayStr = request.getParameter("birthday");

                BigDecimal subtotal = subtotalParam != null ? new BigDecimal(subtotalParam) : BigDecimal.ZERO;
                LocalDate birthday = birthdayStr != null && !birthdayStr.isEmpty() ? LocalDate.parse(birthdayStr) : null;

                List<OrderDiscount> discounts = discountService.evaluateOrderDiscounts(subtotal, pincode, birthday);
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), discounts);
            } else {
                List<Discount> discounts = discountService.getActiveDiscounts();
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), discounts);
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
}
