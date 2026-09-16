package com.akshaya.shopsphere.registration;

import com.akshaya.shopsphere.common.IPasswordService;
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

@WebServlet("/register")
public class RegistrationServlet extends HttpServlet {
    private RegistrationService registrationService;

    @Override
    public void init() throws ServletException {
        super.init();
        IPasswordService passwordService = new PasswordService();
        IRegistrationRepository registrationRepository = new RegistrationRepository(passwordService);
        this.registrationService = new RegistrationService(registrationRepository);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        ObjectMapper objectMapper = JsonUtil.newObjectMapper();

        RegistrationRequest registrationRequest =
                objectMapper.readValue(
                        request.getReader(),
                        RegistrationRequest.class
                );
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {

            boolean registered = registrationService.registerUser(registrationRequest);

            if (registered) {

                response.setStatus(HttpServletResponse.SC_OK);

                objectMapper.writeValue(
                        response.getWriter(),
                        Map.of(
                                "success", true,
                                "message", "Registration successful"
                        )
                );

            } else {

                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                objectMapper.writeValue(
                        response.getWriter(),
                        Map.of(
                                "success", false,
                                "message", "Registration failed"
                        )
                );
            }

        } catch (IllegalArgumentException e) {

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

            objectMapper.writeValue(
                    response.getWriter(),
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );

        } catch (SQLException e) {

            e.printStackTrace();

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            objectMapper.writeValue(
                    response.getWriter(),
                    Map.of(
                            "success", false,
                            "message", "Registration could not be completed. Please try again later."
                    )
            );
        }
    }
}
