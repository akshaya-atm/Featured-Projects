package com.akshaya.shopsphere.product;

import java.math.BigDecimal;
import java.time.LocalDate;

// Read-only projection for the "what should go on sale" admin analytics query -- one row per
// (product, soon-to-expire batch). Not a DB entity of its own; just a convenient shape for
// ProductBatchRepository.getSaleCandidates() to hand back to the caller.
public class SaleCandidate {
    private int productId;
    private String productName;
    private BigDecimal price;
    private LocalDate expiryDate;
    private int availableQuantity;

    public SaleCandidate(int productId, String productName, BigDecimal price, LocalDate expiryDate, int availableQuantity) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.expiryDate = expiryDate;
        this.availableQuantity = availableQuantity;
    }

    public int getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
