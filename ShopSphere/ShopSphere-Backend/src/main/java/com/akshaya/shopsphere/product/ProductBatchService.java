package com.akshaya.shopsphere.product;

import java.sql.SQLException;
import java.util.List;

public class ProductBatchService {
    private IProductBatchRepository batchRepository;

    public ProductBatchService(IProductBatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    public boolean addBatch(ProductBatch batch) throws SQLException {
        if (batch.getProductId() <= 0) {
            throw new IllegalArgumentException("Invalid product ID for batch");
        }
        if (batch.getBatchDate() == null) {
            throw new IllegalArgumentException("Batch date is required");
        }
        if (batch.getAvailableQuantity() < 0) {
            throw new IllegalArgumentException("Available quantity cannot be negative");
        }
        return batchRepository.addBatch(batch);
    }

    public boolean updateBatch(ProductBatch batch) throws SQLException {
        if (batch.getBatchId() <= 0) {
            throw new IllegalArgumentException("Invalid batch ID");
        }
        if (batch.getBatchDate() == null) {
            throw new IllegalArgumentException("Batch date is required");
        }
        if (batch.getAvailableQuantity() < 0) {
            throw new IllegalArgumentException("Available quantity cannot be negative");
        }
        return batchRepository.updateBatch(batch);
    }

    public boolean deleteBatch(int batchId) throws SQLException {
        if (batchId <= 0) {
            throw new IllegalArgumentException("Invalid batch ID");
        }
        return batchRepository.deleteBatch(batchId);
    }

    public List<ProductBatch> getBatchesByProductId(int productId) throws SQLException {
        if (productId <= 0) {
            throw new IllegalArgumentException("Invalid product ID");
        }
        return batchRepository.getBatchesByProductId(productId);
    }

    public ProductBatch getBatchById(int batchId) throws SQLException {
        if (batchId <= 0) {
            throw new IllegalArgumentException("Invalid batch ID");
        }
        return batchRepository.getBatchById(batchId);
    }

    public List<StockMovement> getMovementsByProductId(int productId) throws SQLException {
        if (productId <= 0) {
            throw new IllegalArgumentException("Invalid product ID");
        }
        return batchRepository.getMovementsByProductId(productId);
    }

    public List<SaleCandidate> getSaleCandidates(int withinDays, int minQuantity, int maxResults) throws SQLException {
        if (withinDays <= 0) {
            throw new IllegalArgumentException("withinDays must be a positive number of days");
        }
        if (minQuantity < 0) {
            throw new IllegalArgumentException("minQuantity cannot be negative");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        return batchRepository.getSaleCandidates(withinDays, minQuantity, maxResults);
    }
}
