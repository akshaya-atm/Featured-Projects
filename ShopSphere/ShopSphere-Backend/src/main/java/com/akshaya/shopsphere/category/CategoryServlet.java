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
import java.util.List;
import java.util.Map;

@WebServlet("/categories/*")
public class CategoryServlet extends HttpServlet {

    private CategoryService categoryService;
    private ObjectMapper objectMapper;

    @Override
    public void init() throws ServletException {
        super.init();
        ICategoryRepository categoryRepository = new CategoryRepository();
        this.categoryService = new CategoryService(categoryRepository);
        this.objectMapper = JsonUtil.newObjectMapper();
    }

    // GET /categories
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            List<Category> categories = categoryService.getCategories();
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), categories);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }

    // Category creation/update/deletion require admin auth and live exclusively in AdminCategoryServlet.
}
