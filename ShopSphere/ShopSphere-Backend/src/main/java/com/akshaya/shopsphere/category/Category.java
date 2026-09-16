package com.akshaya.shopsphere.category;

public class Category {
    private int categoryId;
    private String categoryName;
    private String categoryDescription;
    private String categoryImageUrl;
    // Drives the storefront's "Fresh Today" badge (see products.js) when any of a product's categories is perishable.
    private boolean perishable;

    public Category() {
    }

    public Category(int categoryId, String categoryName, String categoryDescription, String categoryImageUrl) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryDescription = categoryDescription;
        this.categoryImageUrl = categoryImageUrl;
    }

    public Category(int categoryId, String categoryName, String categoryDescription, String categoryImageUrl, boolean perishable) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryDescription = categoryDescription;
        this.categoryImageUrl = categoryImageUrl;
        this.perishable = perishable;
    }

    public Category(String categoryName, String categoryDescription, String categoryImageUrl) {
        this.categoryName = categoryName;
        this.categoryDescription = categoryDescription;
        this.categoryImageUrl = categoryImageUrl;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryDescription() {
        return categoryDescription;
    }

    public void setCategoryDescription(String categoryDescription) {
        this.categoryDescription = categoryDescription;
    }

    public String getCategoryImageUrl() {
        return categoryImageUrl;
    }

    public void setCategoryImageUrl(String categoryImageUrl) {
        this.categoryImageUrl = categoryImageUrl;
    }

    public boolean isPerishable() {
        return perishable;
    }

    public void setPerishable(boolean perishable) {
        this.perishable = perishable;
    }
}
