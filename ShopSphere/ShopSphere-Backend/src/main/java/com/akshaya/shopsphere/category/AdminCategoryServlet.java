package com.akshaya.shopsphere.category;

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

@WebServlet("/admin/categories")
public class AdminCategoryServlet extends HttpServlet {

    private CategoryService categoryService;
    private ObjectMapper objectMapper;

    @Override
    public void init() throws ServletException {
        super.init();
        ICategoryRepository categoryRepository = new CategoryRepository();
        this.categoryService = new CategoryService(categoryRepository);
        this.objectMapper = JsonUtil.newObjectMapper();
    }

    // POST /admin/categories -> Create a new category
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            Category category = objectMapper.readValue(request.getReader(), Category.class);
            boolean created = categoryService.addCategory(category);

            if (created) {
                response.setStatus(HttpServletResponse.SC_CREATED);
                objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Category added successfully"));
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Failed to add category"));
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

    // PUT /admin/categories -> Update an existing category
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            Category category = objectMapper.readValue(request.getReader(), Category.class);
            boolean updated = categoryService.updateCategory(category);

            if (updated) {
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Category updated successfully"));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Category not found"));
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

    // DELETE /admin/categories?id=1 -> Delete a category
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String idParam = request.getParameter("id");
        if (idParam == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Category ID is required"));
            return;
        }

        try {
            int categoryId = Integer.parseInt(idParam);
            boolean deleted = categoryService.deleteCategory(categoryId);

            if (deleted) {
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Category deleted successfully"));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Category not found"));
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Invalid category ID format"));
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }
}
