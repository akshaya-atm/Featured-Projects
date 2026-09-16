package com.akshaya.shopsphere.product;

import com.akshaya.shopsphere.category.Category;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Product {
    private int productId;
    private String name;
    private String description;
    private BigDecimal price;
    private String unitValue;
    private BigDecimal discountedPrice;
    private BigDecimal discountPercentage;
    private String imageUrl;
    private List<Category> categories = new ArrayList<>();
    private int totalAvailableQuantity;

    public Product() {
    }

    public Product(int productId, String name, String description, BigDecimal price, String unitValue, BigDecimal discountedPrice, BigDecimal discountPercentage, String imageUrl, List<Category> categories, int totalAvailableQuantity) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.unitValue = unitValue;
        this.discountedPrice = discountedPrice;
        this.discountPercentage = discountPercentage;
        this.imageUrl = imageUrl;
        this.categories = categories != null ? categories : new ArrayList<>();
        this.totalAvailableQuantity = totalAvailableQuantity;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getUnitValue() {
        return unitValue;
    }

    public void setUnitValue(String unitValue) {
        this.unitValue = unitValue;
    }

    public BigDecimal getDiscountedPrice() {
        return discountedPrice;
    }

    public void setDiscountedPrice(BigDecimal discountedPrice) {
        this.discountedPrice = discountedPrice;
    }

    public BigDecimal getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(BigDecimal discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    public int getTotalAvailableQuantity() {
        return totalAvailableQuantity;
    }

    public void setTotalAvailableQuantity(int totalAvailableQuantity) {
        this.totalAvailableQuantity = totalAvailableQuantity;
    }
}
