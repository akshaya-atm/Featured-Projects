package com.akshaya.shopsphere.product;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public class ProductService {
    private IProductRepository productRepository;

    public ProductService(IProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public boolean addProduct(Product product) throws SQLException {
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be empty");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Product price must be non-negative");
        }
        return productRepository.addProduct(product);
    }

    public boolean updateProduct(Product product) throws SQLException {
        if (product.getProductId() <= 0) {
            throw new IllegalArgumentException("Invalid product ID");
        }
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be empty");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Product price must be non-negative");
        }
        return productRepository.updateProduct(product);
    }

    public boolean deleteProduct(int productId) throws SQLException {
        if (productId <= 0) {
            throw new IllegalArgumentException("Invalid product ID");
        }
        return productRepository.deleteProduct(productId);
    }

    public List<Product> getAllProducts() throws SQLException {
        return productRepository.getAllProducts();
    }
    public List<Product> getProductsByCategory(int categoryId) throws SQLException{
        return productRepository.getProductByCategory(categoryId);
    }

    public Product getProductById(int productId) throws SQLException {
        if (productId <= 0) {
            throw new IllegalArgumentException("Invalid product ID");
        }
        return productRepository.getProductById(productId);
    }
}
