package com.akshaya.shopsphere.product;

import com.akshaya.shopsphere.category.Category;
import com.akshaya.shopsphere.db.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductRepository implements IProductRepository {

    @Override
    public boolean addProduct(Product product) throws SQLException {
        String insertProductSql = "INSERT INTO products (name, description, price, unit_value, image_url) VALUES (?, ?, ?, ?, ?)";
        String insertCategorySql = "INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int productId;
                try (PreparedStatement preparedStatement = connection.prepareStatement(insertProductSql, Statement.RETURN_GENERATED_KEYS)) {
                    preparedStatement.setString(1, product.getName());
                    preparedStatement.setString(2, product.getDescription());
                    preparedStatement.setBigDecimal(3, product.getPrice());
                    preparedStatement.setString(4, product.getUnitValue());
                    preparedStatement.setString(5, product.getImageUrl());
                    preparedStatement.executeUpdate();

                    try (ResultSet rs = preparedStatement.getGeneratedKeys()) {
                        if (rs.next()) {
                            productId = rs.getInt(1);
                            product.setProductId(productId);
                        } else {
                            connection.rollback();
                            return false;
                        }
                    }
                }

                if (product.getCategories() != null && !product.getCategories().isEmpty()) {
                    try (PreparedStatement catStatement = connection.prepareStatement(insertCategorySql)) {
                        for (Category cat : product.getCategories()) {
                            catStatement.setInt(1, productId);
                            catStatement.setInt(2, cat.getCategoryId());
                            catStatement.addBatch();
                        }
                        catStatement.executeBatch();
                    }
                }
                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                throw new SQLException(e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean updateProduct(Product product) throws SQLException {
        String updateProductSql = "UPDATE products SET name = ?, description = ?, price = ?, unit_value = ?, image_url = ? WHERE product_id = ?";
        String deleteCategoriesSql = "DELETE FROM product_categories WHERE product_id = ?";
        String insertCategorySql = "INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement preparedStatement = connection.prepareStatement(updateProductSql)) {
                    preparedStatement.setString(1, product.getName());
                    preparedStatement.setString(2, product.getDescription());
                    preparedStatement.setBigDecimal(3, product.getPrice());
                    preparedStatement.setString(4, product.getUnitValue());
                    preparedStatement.setString(5, product.getImageUrl());
                    preparedStatement.setInt(6, product.getProductId());
                    preparedStatement.executeUpdate();
                }

                try (PreparedStatement delStatement = connection.prepareStatement(deleteCategoriesSql)) {
                    delStatement.setInt(1, product.getProductId());
                    delStatement.executeUpdate();
                }

                if (product.getCategories() != null && !product.getCategories().isEmpty()) {
                    try (PreparedStatement catStatement = connection.prepareStatement(insertCategorySql)) {
                        for (Category cat : product.getCategories()) {
                            catStatement.setInt(1, product.getProductId());
                            catStatement.setInt(2, cat.getCategoryId());
                            catStatement.addBatch();
                        }
                        catStatement.executeBatch();
                    }
                }
                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                throw new SQLException(e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean deleteProduct(int productId) throws SQLException {
        String sql = "DELETE FROM products WHERE product_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, productId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    @Override
    public List<Product> getAllProducts() throws SQLException {
        List<Product> products = new ArrayList<>();
        String sql = """
            SELECT 
                p.product_id, 
                p.name, 
                p.description, 
                p.price, 
                p.unit_value,
                p.image_url, 
                COALESCE((SELECT SUM(pb.available_quantity) FROM product_batches pb 
                          WHERE pb.product_id = p.product_id AND (pb.expiry_date IS NULL OR pb.expiry_date >= CURRENT_DATE)), 0) AS total_stock,
                MIN(
                    CASE 
                        WHEN d.discount_type = 'PERCENTAGE' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until
                            THEN ROUND(p.price * (1 - d.discount_value / 100.0), 2)
                        WHEN d.discount_type = 'FLAT_AMOUNT' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until
                            THEN GREATEST(0.00, p.price - d.discount_value)
                        ELSE NULL
                    END
                ) AS discounted_price,
                MAX(
                    CASE
                        WHEN d.discount_type = 'PERCENTAGE' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until
                            THEN d.discount_value
                        WHEN d.discount_type = 'FLAT_AMOUNT' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until AND p.price > 0
                            THEN ROUND((d.discount_value / p.price) * 100.0, 0)
                        ELSE NULL
                    END
                ) AS discount_percentage
            FROM products p
            LEFT JOIN product_discounts pd ON p.product_id = pd.product_id
            LEFT JOIN discounts d ON pd.discount_id = d.discount_id
            GROUP BY p.product_id, p.name, p.description, p.price, p.unit_value, p.image_url
            ORDER BY p.product_id ASC
            """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                int productId = resultSet.getInt("product_id");
                List<Category> categories = getCategoriesForProduct(connection, productId);

                Product product = new Product(
                        productId,
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getBigDecimal("price"),
                        resultSet.getString("unit_value"),
                        resultSet.getBigDecimal("discounted_price"),
                        resultSet.getBigDecimal("discount_percentage"),
                        resultSet.getString("image_url"),
                        categories,
                        resultSet.getInt("total_stock")
                );
                products.add(product);
            }
        }
        return products;
    }

    @Override
    public Product getProductById(int productId) throws SQLException {
        String sql = """
            SELECT 
                p.product_id, 
                p.name, 
                p.description, 
                p.price, 
                p.unit_value,
                p.image_url, 
                COALESCE((SELECT SUM(pb.available_quantity) FROM product_batches pb 
                          WHERE pb.product_id = p.product_id AND (pb.expiry_date IS NULL OR pb.expiry_date >= CURRENT_DATE)), 0) AS total_stock,
                MIN(
                    CASE 
                        WHEN d.discount_type = 'PERCENTAGE' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until
                            THEN ROUND(p.price * (1 - d.discount_value / 100.0), 2)
                        WHEN d.discount_type = 'FLAT_AMOUNT' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until
                            THEN GREATEST(0.00, p.price - d.discount_value)
                        ELSE NULL
                    END
                ) AS discounted_price,
                MAX(
                    CASE
                        WHEN d.discount_type = 'PERCENTAGE' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until
                            THEN d.discount_value
                        WHEN d.discount_type = 'FLAT_AMOUNT' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until AND p.price > 0
                            THEN ROUND((d.discount_value / p.price) * 100.0, 0)
                        ELSE NULL
                    END
                ) AS discount_percentage
            FROM products p
            LEFT JOIN product_discounts pd ON p.product_id = pd.product_id
            LEFT JOIN discounts d ON pd.discount_id = d.discount_id
            WHERE p.product_id = ?
            GROUP BY p.product_id, p.name, p.description, p.price, p.unit_value, p.image_url
            """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, productId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    List<Category> categories = getCategoriesForProduct(connection, productId);
                    return new Product(
                            productId,
                            resultSet.getString("name"),
                            resultSet.getString("description"),
                            resultSet.getBigDecimal("price"),
                            resultSet.getString("unit_value"),
                            resultSet.getBigDecimal("discounted_price"),
                            resultSet.getBigDecimal("discount_percentage"),
                            resultSet.getString("image_url"),
                            categories,
                            resultSet.getInt("total_stock")
                    );
                }
            }
        }
        return null;
    }
    @Override
    public List<Product> getProductByCategory(int categoryId) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = """
                SELECT
                    p.product_id,
                    p.name,
                    p.description,
                    p.price,
                    p.unit_value,
                    p.image_url,
                    COALESCE((SELECT SUM(pb.available_quantity) FROM product_batches pb 
                              WHERE pb.product_id = p.product_id AND (pb.expiry_date IS NULL OR pb.expiry_date >= CURRENT_DATE)), 0) AS total_stock,
                    MIN(
                        CASE
                            WHEN d.discount_type = 'PERCENTAGE' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until
                                THEN ROUND(p.price * (1 - d.discount_value / 100.0), 2)
                            WHEN d.discount_type = 'FLAT_AMOUNT' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until
                                THEN GREATEST(0.00, p.price - d.discount_value)
                            ELSE NULL
                        END
                    ) AS discounted_price,
                    MAX(
                        CASE
                            WHEN d.discount_type = 'PERCENTAGE' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until
                                THEN d.discount_value
                            WHEN d.discount_type = 'FLAT_AMOUNT' AND CURRENT_TIMESTAMP BETWEEN d.valid_from AND d.valid_until AND p.price > 0
                                THEN ROUND((d.discount_value / p.price) * 100.0, 0)
                            ELSE NULL
                        END
                    ) AS discount_percentage
                FROM products p
                JOIN product_categories pc ON p.product_id = pc.product_id
                LEFT JOIN product_discounts pd ON p.product_id = pd.product_id
                LEFT JOIN discounts d ON pd.discount_id = d.discount_id
                WHERE pc.category_id = ?
                GROUP BY p.product_id, p.name, p.description, p.price, p.unit_value, p.image_url
                ORDER BY p.product_id ASC
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, categoryId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("product_id");
                    List<Category> categories = getCategoriesForProduct(connection, productId);
                    list.add(new Product(
                            productId,
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getBigDecimal("price"),
                            rs.getString("unit_value"),
                            rs.getBigDecimal("discounted_price"),
                            rs.getBigDecimal("discount_percentage"),
                            rs.getString("image_url"),
                            categories,
                            rs.getInt("total_stock")
                    ));
                }
            }
        }
        return list;
    }

    private List<Category> getCategoriesForProduct(Connection connection, int productId) throws SQLException {
        List<Category> list = new ArrayList<>();
        String sql = """
            SELECT c.category_id, c.category_name, c.category_description, c.category_image_url, c.is_perishable
            FROM categories c
            JOIN product_categories pc ON c.category_id = pc.category_id
            WHERE pc.product_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new Category(
                            rs.getInt("category_id"),
                            rs.getString("category_name"),
                            rs.getString("category_description"),
                            rs.getString("category_image_url"),
                            rs.getBoolean("is_perishable")
                    ));
                }
            }
        }
        return list;
    }
}
