package com.akshaya.shopsphere.product;

import java.sql.SQLException;
import java.util.List;

public interface IProductRepository {
    boolean addProduct(Product product) throws SQLException;
    boolean updateProduct(Product product) throws SQLException;
    boolean deleteProduct(int productId) throws SQLException;
    List<Product> getAllProducts() throws SQLException;
    Product getProductById(int productId) throws SQLException;
    List<Product> getProductByCategory(int categoryId) throws SQLException;
}
