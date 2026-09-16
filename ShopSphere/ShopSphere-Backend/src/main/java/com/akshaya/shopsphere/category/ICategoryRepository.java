package com.akshaya.shopsphere.category;

import java.sql.SQLException;
import java.util.List;

public interface ICategoryRepository {
    boolean addCategory(Category category) throws SQLException;
    boolean updateCategory(Category category) throws SQLException;
    boolean deleteCategory(int categoryId) throws SQLException;
    List<Category> getCategories() throws SQLException;
}
