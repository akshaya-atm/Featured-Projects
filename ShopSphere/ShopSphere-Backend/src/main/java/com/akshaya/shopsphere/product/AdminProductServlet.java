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
import java.util.Map;

@WebServlet("/admin/products/*")
public class AdminProductServlet extends HttpServlet {

    private ProductService productService;
    private ObjectMapper objectMapper;

    @Override
    public void init() throws ServletException {
        super.init();
        IProductRepository productRepository = new ProductRepository();
        this.productService = new ProductService(productRepository);
        this.objectMapper = JsonUtil.newObjectMapper();
    }

    // POST /admin/products -> Add Product
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            Product product = objectMapper.readValue(request.getReader(), Product.class);
            boolean created = productService.addProduct(product);
            if (created) {
                response.setStatus(HttpServletResponse.SC_CREATED);
                objectMapper.writeValue(response.getWriter(), Map.of(
                        "success", true,
                        "message", "Product added successfully",
                        "productId", product.getProductId()
                ));
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Failed to add product"));
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", e.getMessage()));
        } catch (SQLException e) {
            // order_items.product_id has no ON DELETE CASCADE (by design, to preserve order history),
            // so deleting a product still referenced by an order raises FK violation 23503 here.
            if ("23503".equals(e.getSQLState())) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message",
                        "Can't delete this product — it appears in one or more existing customer orders, " +
                        "and deleting it would break that order history. Remove its stock batches instead " +
                        "if you want it to stop showing as available."));
                return;
            }
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }

    // PUT /admin/products -> Update Product
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            Product product = objectMapper.readValue(request.getReader(), Product.class);
            boolean updated = productService.updateProduct(product);
            if (updated) {
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Product updated successfully"));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Product not found"));
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", e.getMessage()));
        } catch (SQLException e) {
            // order_items.product_id has no ON DELETE CASCADE (by design, to preserve order history),
            // so deleting a product still referenced by an order raises FK violation 23503 here.
            if ("23503".equals(e.getSQLState())) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message",
                        "Can't delete this product — it appears in one or more existing customer orders, " +
                        "and deleting it would break that order history. Remove its stock batches instead " +
                        "if you want it to stop showing as available."));
                return;
            }
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }

    // DELETE /admin/products?id=1 -> Delete Product
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String idParam = request.getParameter("id");
        if (idParam == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Product ID is required"));
            return;
        }

        try {
            int productId = Integer.parseInt(idParam);
            boolean deleted = productService.deleteProduct(productId);
            if (deleted) {
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), Map.of("success", true, "message", "Product deleted successfully"));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Product not found"));
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Invalid product ID format"));
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", e.getMessage()));
        } catch (SQLException e) {
            // order_items.product_id has no ON DELETE CASCADE (by design, to preserve order history),
            // so deleting a product still referenced by an order raises FK violation 23503 here.
            if ("23503".equals(e.getSQLState())) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message",
                        "Can't delete this product — it appears in one or more existing customer orders, " +
                        "and deleting it would break that order history. Remove its stock batches instead " +
                        "if you want it to stop showing as available."));
                return;
            }
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Something went wrong on our end. Please try again in a moment."));
        }
    }
}
