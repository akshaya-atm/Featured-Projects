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

@WebServlet("/batches")
public class ProductBatchServlet extends HttpServlet {

    private ProductBatchService batchService;
    private ObjectMapper objectMapper;

    @Override
    public void init() throws ServletException {
        super.init();
        IProductBatchRepository batchRepository = new ProductBatchRepository();
        this.batchService = new ProductBatchService(batchRepository);
        this.objectMapper = JsonUtil.newObjectMapper();
    }

    // GET /batches?productId=101 OR /batches?id=501 (Public / Read-only)
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String productIdParam = request.getParameter("productId");
        String batchIdParam = request.getParameter("id");

        try {
            if (productIdParam != null) {
                int productId = Integer.parseInt(productIdParam);
                List<ProductBatch> batches = batchService.getBatchesByProductId(productId);
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), batches);
            } else if (batchIdParam != null) {
                int batchId = Integer.parseInt(batchIdParam);
                ProductBatch batch = batchService.getBatchById(batchId);
                if (batch != null) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    objectMapper.writeValue(response.getWriter(), batch);
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Batch not found"));
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Parameter productId or id is required"));
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Invalid ID format"));
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
