-- Audit trail of automatic stock changes (sale decrements and compensating restores). Manual
-- admin edits to available_quantity are not logged here.
CREATE TABLE stock_movements (
    movement_id SERIAL PRIMARY KEY,
    batch_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    quantity_change INTEGER NOT NULL,
    movement_type VARCHAR(50) NOT NULL,
    reference_order_id INTEGER, -- not a foreign key; populated for future use
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (batch_id) REFERENCES product_batches(batch_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);

CREATE INDEX idx_stock_movements_batch ON stock_movements(batch_id);
CREATE INDEX idx_stock_movements_product ON stock_movements(product_id);
