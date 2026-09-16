-- 1. Modify cart_items quantity to INTEGER
ALTER TABLE cart_items ALTER COLUMN quantity TYPE INTEGER USING quantity::INTEGER;

-- 2. Modify order_items quantity to INTEGER
ALTER TABLE order_items ALTER COLUMN quantity TYPE INTEGER USING quantity::INTEGER;
