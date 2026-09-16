package com.akshaya.shopsphere.category;

import com.akshaya.shopsphere.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CategoryRepository implements ICategoryRepository {

    @Override
    public boolean addCategory(Category category) throws SQLException {
        String sql = "INSERT INTO categories (category_name, category_description, category_image_url, is_perishable) VALUES (?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, category.getCategoryName());
            preparedStatement.setString(2, category.getCategoryDescription());
            preparedStatement.setString(3, category.getCategoryImageUrl());
            preparedStatement.setBoolean(4, category.isPerishable());
            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        }
    }

    @Override
    public boolean updateCategory(Category category) throws SQLException {
        String sql = "UPDATE categories SET category_name = ?, category_description = ?, category_image_url = ?, is_perishable = ? WHERE category_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, category.getCategoryName());
            preparedStatement.setString(2, category.getCategoryDescription());
            preparedStatement.setString(3, category.getCategoryImageUrl());
            preparedStatement.setBoolean(4, category.isPerishable());
            preparedStatement.setInt(5, category.getCategoryId());
            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        }
    }

    @Override
    public boolean deleteCategory(int categoryId) throws SQLException {
        String sql = "DELETE FROM categories WHERE category_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, categoryId);
            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        }
    }

    @Override
    public List<Category> getCategories() throws SQLException {
        List<Category> categories = new ArrayList<>();
        String sql = "SELECT DISTINCT ON (category_name) category_id, category_name, category_description, category_image_url, is_perishable FROM categories ORDER BY category_name, category_id";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                Category category = new Category(
                        resultSet.getInt("category_id"),
                        resultSet.getString("category_name"),
                        resultSet.getString("category_description"),
                        resultSet.getString("category_image_url"),
                        resultSet.getBoolean("is_perishable")
                );
                categories.add(category);
            }
        }
        return categories;
    }
}
