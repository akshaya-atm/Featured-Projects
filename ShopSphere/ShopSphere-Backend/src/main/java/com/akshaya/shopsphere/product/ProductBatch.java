package com.akshaya.shopsphere.product;

import java.time.LocalDate;

public class ProductBatch {
    private int batchId;
    private int productId;
    private String source;
    private LocalDate batchDate;
    private LocalDate expiryDate;
    private int availableQuantity;

    public ProductBatch() {
    }

    public ProductBatch(int batchId, int productId, String source, LocalDate batchDate, LocalDate expiryDate, int availableQuantity) {
        this.batchId = batchId;
        this.productId = productId;
        this.source = source;
        this.batchDate = batchDate;
        this.expiryDate = expiryDate;
        this.availableQuantity = availableQuantity;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDate getBatchDate() {
        return batchDate;
    }

    public void setBatchDate(LocalDate batchDate) {
        this.batchDate = batchDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }
}
