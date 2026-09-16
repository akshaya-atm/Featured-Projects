package com.akshaya.shopsphere.auth;

import com.akshaya.shopsphere.common.IPasswordService;
import com.akshaya.shopsphere.common.JwtUtil;
import com.akshaya.shopsphere.common.PasswordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.akshaya.shopsphere.common.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private LoginService loginService;
    private ObjectMapper objectMapper;

    @Override
    public void init() throws ServletException {
        super.init();
        IPasswordService passwordService = new PasswordService();
        ILoginRepository loginRepository = new LoginRepository();
        this.loginService = new LoginService(passwordService, loginRepository);
        this.objectMapper = JsonUtil.newObjectMapper();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            LoginRequest loginRequest = objectMapper.readValue(request.getReader(), LoginRequest.class);
            UserAuthInfo userAuth = loginService.getAuthenticatedUser(loginRequest.email(), loginRequest.password());

            if (userAuth != null) {
                String token = JwtUtil.generateToken(userAuth.userId(), userAuth.userType(), loginRequest.email());

                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), Map.of(
                        "success", true,
                        "token", token,
                        "name", userAuth.name(),
                        "userType", userAuth.userType()
                ));
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                objectMapper.writeValue(response.getWriter(), Map.of(
                        "success", false,
                        "message", "Invalid email or password!"
                ));
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "message", e.getMessage() != null ? e.getMessage() : "Please check your details and try again."
            ));
        } catch (SQLException e) {
            // Log the real cause server-side; customer only sees the friendly message below.
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "message", "Something went wrong on our end. Please try again in a moment."
            ));
        }
    }
}
