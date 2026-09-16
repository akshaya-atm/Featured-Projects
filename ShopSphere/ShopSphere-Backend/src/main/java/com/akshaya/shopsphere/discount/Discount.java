package com.akshaya.shopsphere.discount;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Discount {
    private int discountId;
    private String name;
    private String discountType; // "PERCENTAGE" or "FLAT_AMOUNT"
    private BigDecimal discountValue;
    private String targetScope; // "PRODUCT", "AREA", "MIN_ORDER", "BIRTHDAY", "CUSTOMER"
    private BigDecimal minOrderAmount;
    private String targetPincode;
    private Integer targetUserId;
    private boolean isBirthdayOnly;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    // Not a column on `discounts` -- comes from the product_discounts junction table (see DiscountRepository)
    private Integer productId;

    public Discount() {
    }

    public Discount(int discountId, String name, String discountType, BigDecimal discountValue, String targetScope, BigDecimal minOrderAmount, String targetPincode, Integer targetUserId, boolean isBirthdayOnly, LocalDateTime validFrom, LocalDateTime validUntil) {
        this.discountId = discountId;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.targetScope = targetScope;
        this.minOrderAmount = minOrderAmount;
        this.targetPincode = targetPincode;
        this.targetUserId = targetUserId;
        this.isBirthdayOnly = isBirthdayOnly;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    public int getDiscountId() {
        return discountId;
    }

    public void setDiscountId(int discountId) {
        this.discountId = discountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public String getTargetScope() {
        return targetScope;
    }

    public void setTargetScope(String targetScope) {
        this.targetScope = targetScope;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public void setMinOrderAmount(BigDecimal minOrderAmount) {
        this.minOrderAmount = minOrderAmount;
    }

    public String getTargetPincode() {
        return targetPincode;
    }

    public void setTargetPincode(String targetPincode) {
        this.targetPincode = targetPincode;
    }

    public Integer getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Integer targetUserId) {
        this.targetUserId = targetUserId;
    }

    public boolean isBirthdayOnly() {
        return isBirthdayOnly;
    }

    public void setBirthdayOnly(boolean birthdayOnly) {
        isBirthdayOnly = birthdayOnly;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }
}
