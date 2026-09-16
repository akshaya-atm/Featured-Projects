package com.akshaya.shopsphere.product;

import com.akshaya.shopsphere.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductBatchRepository implements IProductBatchRepository {

    @Override
    public boolean addBatch(ProductBatch batch) throws SQLException {
        String sql = "INSERT INTO product_batches (product_id, source, batch_date, expiry_date, available_quantity) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, batch.getProductId());
            preparedStatement.setString(2, batch.getSource());
            preparedStatement.setDate(3, Date.valueOf(batch.getBatchDate()));
            if (batch.getExpiryDate() != null) {
                preparedStatement.setDate(4, Date.valueOf(batch.getExpiryDate()));
            } else {
                preparedStatement.setNull(4, java.sql.Types.DATE);
            }
            preparedStatement.setInt(5, batch.getAvailableQuantity());
            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        }
    }

    @Override
    public boolean updateBatch(ProductBatch batch) throws SQLException {
        String sql = "UPDATE product_batches SET source = ?, batch_date = ?, expiry_date = ?, available_quantity = ? WHERE batch_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, batch.getSource());
            preparedStatement.setDate(2, Date.valueOf(batch.getBatchDate()));
            if (batch.getExpiryDate() != null) {
                preparedStatement.setDate(3, Date.valueOf(batch.getExpiryDate()));
            } else {
                preparedStatement.setNull(3, java.sql.Types.DATE);
            }
            preparedStatement.setInt(4, batch.getAvailableQuantity());
            preparedStatement.setInt(5, batch.getBatchId());
            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        }
    }

    @Override
    public boolean deleteBatch(int batchId) throws SQLException {
        String sql = "DELETE FROM product_batches WHERE batch_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, batchId);
            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        }
    }

    @Override
    public List<ProductBatch> getBatchesByProductId(int productId) throws SQLException {
        List<ProductBatch> batches = new ArrayList<>();
        String sql = """
            SELECT batch_id, product_id, source, batch_date, expiry_date, available_quantity
            FROM product_batches
            WHERE product_id = ?
            ORDER BY expiry_date ASC
            """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, productId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    ProductBatch batch = new ProductBatch(
                            resultSet.getInt("batch_id"),
                            resultSet.getInt("product_id"),
                            resultSet.getString("source"),
                            resultSet.getDate("batch_date").toLocalDate(),
                            resultSet.getDate("expiry_date") != null ? resultSet.getDate("expiry_date").toLocalDate() : null,
                            resultSet.getInt("available_quantity")
                    );
                    batches.add(batch);
                }
            }
        }
        return batches;
    }

    @Override
    public ProductBatch getBatchById(int batchId) throws SQLException {
        String sql = "SELECT batch_id, product_id, source, batch_date, expiry_date, available_quantity FROM product_batches WHERE batch_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, batchId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return new ProductBatch(
                            resultSet.getInt("batch_id"),
                            resultSet.getInt("product_id"),
                            resultSet.getString("source"),
                            resultSet.getDate("batch_date").toLocalDate(),
                            resultSet.getDate("expiry_date") != null ? resultSet.getDate("expiry_date").toLocalDate() : null,
                            resultSet.getInt("available_quantity")
                    );
                }
            }
        }
        return null;
    }

    @Override
    public List<StockMovement> getMovementsByProductId(int productId) throws SQLException {
        List<StockMovement> movements = new ArrayList<>();
        String sql = """
            SELECT movement_id, batch_id, product_id, quantity_change, movement_type, reference_order_id, created_at
            FROM stock_movements
            WHERE product_id = ?
            ORDER BY created_at DESC, movement_id DESC
            """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, productId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    int referenceOrderId = resultSet.getInt("reference_order_id");
                    movements.add(new StockMovement(
                            resultSet.getInt("movement_id"),
                            resultSet.getInt("batch_id"),
                            resultSet.getInt("product_id"),
                            resultSet.getInt("quantity_change"),
                            resultSet.getString("movement_type"),
                            resultSet.wasNull() ? null : referenceOrderId,
                            resultSet.getTimestamp("created_at").toLocalDateTime()
                    ));
                }
            }
        }
        return movements;
    }

    @Override
    public boolean decrementStock(int productId, int quantity) throws SQLException {
        if (quantity <= 0) {
            return true;
        }

        // Lock this product's usable batches (not expired, still has stock) in FEFO order
        // so two concurrent checkouts can't both deduct from the same units.
        String selectSql = """
            SELECT batch_id, available_quantity
            FROM product_batches
            WHERE product_id = ? AND available_quantity > 0
              AND (expiry_date IS NULL OR expiry_date >= CURRENT_DATE)
            ORDER BY expiry_date ASC
            FOR UPDATE
            """;
        String updateSql = "UPDATE product_batches SET available_quantity = available_quantity - ? WHERE batch_id = ?";
        String movementSql = "INSERT INTO stock_movements (batch_id, product_id, quantity_change, movement_type) VALUES (?, ?, ?, 'SALE')";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int remaining = quantity;
                try (PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
                    selectStatement.setInt(1, productId);
                    try (ResultSet rs = selectStatement.executeQuery();
                         PreparedStatement updateStatement = connection.prepareStatement(updateSql);
                         PreparedStatement movementStatement = connection.prepareStatement(movementSql)) {
                        while (remaining > 0 && rs.next()) {
                            int batchId = rs.getInt("batch_id");
                            int available = rs.getInt("available_quantity");
                            int take = Math.min(available, remaining);

                            updateStatement.setInt(1, take);
                            updateStatement.setInt(2, batchId);
                            updateStatement.executeUpdate();

                            // Ledger entry for this specific batch deduction — same transaction,
                            // same all-or-nothing outcome as the quantity update above.
                            movementStatement.setInt(1, batchId);
                            movementStatement.setInt(2, productId);
                            movementStatement.setInt(3, -take);
                            movementStatement.executeUpdate();

                            remaining -= take;
                        }
                    }
                }

                if (remaining > 0) {
                    // Not enough stock across all usable batches — undo any partial deductions.
                    connection.rollback();
                    return false;
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
    public void restoreStock(int productId, int quantity, String movementType) throws SQLException {
        if (quantity <= 0) {
            return;
        }
        // Best-effort: add the quantity back onto whichever batch currently has the latest
        // expiry date for this product. Used only to compensate a partially-applied order
        // when a later item in the same checkout fails its own stock check.
        String selectTargetBatchSql = """
            SELECT batch_id FROM product_batches
            WHERE product_id = ?
            ORDER BY expiry_date DESC NULLS LAST
            LIMIT 1
            """;
        String updateSql = "UPDATE product_batches SET available_quantity = available_quantity + ? WHERE batch_id = ?";
        String movementSql = "INSERT INTO stock_movements (batch_id, product_id, quantity_change, movement_type) VALUES (?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Integer targetBatchId = null;
                try (PreparedStatement selectStatement = connection.prepareStatement(selectTargetBatchSql)) {
                    selectStatement.setInt(1, productId);
                    try (ResultSet rs = selectStatement.executeQuery()) {
                        if (rs.next()) {
                            targetBatchId = rs.getInt("batch_id");
                        }
                    }
                }

                if (targetBatchId != null) {
                    try (PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
                        updateStatement.setInt(1, quantity);
                        updateStatement.setInt(2, targetBatchId);
                        updateStatement.executeUpdate();
                    }

                    // Ledger entry for the compensating restore — same transaction as the
                    // quantity update above.
                    try (PreparedStatement movementStatement = connection.prepareStatement(movementSql)) {
                        movementStatement.setInt(1, targetBatchId);
                        movementStatement.setInt(2, productId);
                        movementStatement.setInt(3, quantity);
                        movementStatement.setString(4, movementType);
                        movementStatement.executeUpdate();
                    }
                }

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw new SQLException(e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<SaleCandidate> getSaleCandidates(int withinDays, int minQuantity, int maxResults) throws SQLException {
        List<SaleCandidate> candidates = new ArrayList<>();
        // ORDER BY soonest-expiring, highest-stock first, then LIMIT -- so even if a lot of the
        // catalog technically qualifies (a grocery store's fresh categories all having short
        // shelf life is normal), the admin only sees the handful most worth acting on right now,
        // not the whole perishable aisle.
        String sql = """
            SELECT p.product_id, p.name, p.price, pb.expiry_date, pb.available_quantity
            FROM product_batches pb
            JOIN products p ON p.product_id = pb.product_id
            WHERE pb.expiry_date IS NOT NULL
              AND pb.expiry_date BETWEEN CURRENT_DATE AND (CURRENT_DATE + (? * INTERVAL '1 day'))::date
              AND pb.available_quantity >= ?
            ORDER BY pb.expiry_date ASC, pb.available_quantity DESC
            LIMIT ?
            """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, withinDays);
            preparedStatement.setInt(2, minQuantity);
            preparedStatement.setInt(3, maxResults);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    candidates.add(new SaleCandidate(
                            resultSet.getInt("product_id"),
                            resultSet.getString("name"),
                            resultSet.getBigDecimal("price"),
                            resultSet.getDate("expiry_date").toLocalDate(),
                            resultSet.getInt("available_quantity")
                    ));
                }
            }
        }
        return candidates;
    }
}
