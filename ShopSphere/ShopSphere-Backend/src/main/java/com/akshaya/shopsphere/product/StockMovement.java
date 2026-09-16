package com.akshaya.shopsphere.product;

import java.time.LocalDateTime;

// Read-only view of a row in the stock_movements ledger; movements are written only internally
// by ProductBatchRepository (decrementStock()/restoreStock()), never directly by an admin.
public class StockMovement {
    private int movementId;
    private int batchId;
    private int productId;
    private int quantityChange;
    private String movementType;
    private Integer referenceOrderId;
    private LocalDateTime createdAt;

    public StockMovement() {
    }

    public StockMovement(int movementId, int batchId, int productId, int quantityChange, String movementType, Integer referenceOrderId, LocalDateTime createdAt) {
        this.movementId = movementId;
        this.batchId = batchId;
        this.productId = productId;
        this.quantityChange = quantityChange;
        this.movementType = movementType;
        this.referenceOrderId = referenceOrderId;
        this.createdAt = createdAt;
    }

    public int getMovementId() {
        return movementId;
    }

    public void setMovementId(int movementId) {
        this.movementId = movementId;
    }

    public int getBatchId() {
        return batchId;
    }

    public void setBatchId(int batchId) {
        this.batchId = batchId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public String getMovementType() {
        return movementType;
    }

    public void setMovementType(String movementType) {
        this.movementType = movementType;
    }

    public Integer getReferenceOrderId() {
        return referenceOrderId;
    }

    public void setReferenceOrderId(Integer referenceOrderId) {
        this.referenceOrderId = referenceOrderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
