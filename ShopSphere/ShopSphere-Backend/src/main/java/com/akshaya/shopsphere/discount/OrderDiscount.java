package com.akshaya.shopsphere.discount;

import java.math.BigDecimal;

public class OrderDiscount {
    private int discountId;
    private String name;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal calculatedSavings;

    public OrderDiscount() {
    }

    public OrderDiscount(int discountId, String name, String discountType, BigDecimal discountValue, BigDecimal calculatedSavings) {
        this.discountId = discountId;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.calculatedSavings = calculatedSavings;
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

    public BigDecimal getCalculatedSavings() {
        return calculatedSavings;
    }

    public void setCalculatedSavings(BigDecimal calculatedSavings) {
        this.calculatedSavings = calculatedSavings;
    }
}
