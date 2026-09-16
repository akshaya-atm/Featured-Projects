package com.akshaya.shopsphere.product;

import java.sql.SQLException;
import java.util.List;

public interface IProductBatchRepository {
    boolean addBatch(ProductBatch batch) throws SQLException;
    boolean updateBatch(ProductBatch batch) throws SQLException;
    boolean deleteBatch(int batchId) throws SQLException;
    List<ProductBatch> getBatchesByProductId(int productId) throws SQLException;
    ProductBatch getBatchById(int batchId) throws SQLException;

    // Atomically deducts `quantity` units from this product's non-expired batches, earliest
    // expiry first (FEFO), row-locking so concurrent checkouts can't both oversell the same
    // stock. Returns false (and rolls back, deducting nothing) if there isn't enough stock.
    boolean decrementStock(int productId, int quantity) throws SQLException;

    // Best-effort compensator: adds `quantity` back onto one of the product's batches, logging
    // a stock_movements row with the given movementType (e.g. "RESTORE" for undoing an
    // already-applied decrementStock() when a later item in the same order fails, or
    // "CANCELLATION" when an order is cancelled after being placed).
    void restoreStock(int productId, int quantity, String movementType) throws SQLException;

    // Read-only stock movement history for a product (sales + restores), most recent first.
    List<StockMovement> getMovementsByProductId(int productId) throws SQLException;

    // Sale-candidate analytics: products with a batch expiring within `withinDays` days that
    // still carries at least `minQuantity` units -- the classic "this will expire soon and
    // there's a lot left, discount it to move stock" retail signal. Capped at `maxResults` rows
    // (nearest expiry, then highest stock, first) so a catalog where most items are perishable
    // doesn't just dump the whole fresh aisle back at the admin.
    List<SaleCandidate> getSaleCandidates(int withinDays, int minQuantity, int maxResults) throws SQLException;
}
