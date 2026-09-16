package com.akshaya.shopsphere.category;

import java.sql.SQLException;
import java.util.List;

public class CategoryService {
    private ICategoryRepository categoryRepository;

    public CategoryService(ICategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public boolean addCategory(Category category) throws SQLException {
        if (category.getCategoryName() == null || category.getCategoryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be empty");
        }
        return categoryRepository.addCategory(category);
    }

    public boolean updateCategory(Category category) throws SQLException {
        if (category.getCategoryId() <= 0) {
            throw new IllegalArgumentException("Invalid category ID");
        }
        if (category.getCategoryName() == null || category.getCategoryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be empty");
        }
        return categoryRepository.updateCategory(category);
    }

    public boolean deleteCategory(int categoryId) throws SQLException {
        if (categoryId <= 0) {
            throw new IllegalArgumentException("Invalid category ID");
        }
        return categoryRepository.deleteCategory(categoryId);
    }

    public List<Category> getCategories() throws SQLException {
        return categoryRepository.getCategories();
    }
}
