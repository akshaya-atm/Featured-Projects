package com.akshaya.shopsphere.product;

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

@WebServlet("/products/*")
public class ProductServlet extends HttpServlet {

    private ProductService productService;
    private ObjectMapper objectMapper;

    @Override
    public void init() throws ServletException {
        super.init();
        // DB initialization now happens once at app startup via StartupListener, not here.
        IProductRepository productRepository = new ProductRepository();
        this.productService = new ProductService(productRepository);
        this.objectMapper = JsonUtil.newObjectMapper();
    }

    // GET /products OR /products/{id}
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                List<Product> products = productService.getAllProducts();
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), products);
            } else {
                String idStr = pathInfo.substring(1);
                int productId = Integer.parseInt(idStr);
                Product product = productService.getProductById(productId);

                if (product != null) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    objectMapper.writeValue(response.getWriter(), product);
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Product not found"));
                }
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Invalid product ID format"));
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", e.getMessage()));
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }

    // Create/update/delete live exclusively in AdminProductServlet ("/admin/products", gated by
    // AdminFilter); this endpoint is public and read-only.
}
