package com.akshaya.shopsphere.profile;

import com.akshaya.shopsphere.common.IPasswordService;
import com.akshaya.shopsphere.common.JsonUtil;
import com.akshaya.shopsphere.common.PasswordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@WebServlet("/profile/*")
public class ProfileServlet extends HttpServlet {

    private ProfileService profileService;
    private ObjectMapper objectMapper;

    @Override
    public void init() throws ServletException {
        super.init();
        IProfileRepository profileRepository = new ProfileRepository();
        IPasswordService passwordService = new PasswordService();
        this.profileService = new ProfileService(profileRepository, passwordService);
        this.objectMapper = JsonUtil.newObjectMapper();
    }

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

        try {
            ProfileResponse profile = profileService.getProfile(userId);
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), profile);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", e.getMessage()));
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong."));
        }
    }

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
        
        try {
            if (pathInfo != null && pathInfo.equals("/password")) {
                ChangePasswordRequest req = objectMapper.readValue(request.getReader(), ChangePasswordRequest.class);
                boolean updated = profileService.changePassword(userId, req);
                if (updated) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Password updated successfully"));
                } else {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Failed to update password"));
                }
            } else {
                UpdateProfileRequest req = objectMapper.readValue(request.getReader(), UpdateProfileRequest.class);
                boolean updated = profileService.updateProfile(userId, req);
                if (updated) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Profile updated successfully"));
                } else {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Failed to update profile"));
                }
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", e.getMessage()));
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong."));
        }
    }
}
