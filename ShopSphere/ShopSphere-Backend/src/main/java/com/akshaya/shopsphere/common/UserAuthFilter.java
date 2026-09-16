package com.akshaya.shopsphere.common;

import com.akshaya.shopsphere.db.DatabaseConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

@WebFilter(urlPatterns = {"/cart", "/checkout", "/orders/*", "/user/*", "/addresses/*", "/profile/*"})
public class UserAuthFilter implements Filter {

    private ObjectMapper objectMapper;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.objectMapper = JsonUtil.newObjectMapper();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = JwtUtil.validateAndExtractClaims(token);
            Integer userId = claims.get("userId", Integer.class);

            // Verify user exists in the database to prevent database constraint crashes on stale tokens
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users WHERE user_id = ?")) {
                statement.setInt(1, userId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "User does not exist");
                        return;
                    }
                }
            }

            httpRequest.setAttribute("userId", userId);
            httpRequest.setAttribute("userType", claims.get("userType", String.class));
            httpRequest.setAttribute("email", claims.getSubject());

            chain.doFilter(request, response);
        } catch (Exception e) {
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT token");
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", message));
    }

    @Override
    public void destroy() {
    }
}
