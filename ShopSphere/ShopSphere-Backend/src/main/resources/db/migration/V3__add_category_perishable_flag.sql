-- Drives the storefront's "Fresh Today" badge: a product shows it if ANY of its categories is marked perishable.
ALTER TABLE categories ADD COLUMN is_perishable BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill the "fresh" categories.
UPDATE categories SET is_perishable = TRUE
WHERE category_name IN ('Fresh Produce', 'Organic & Bio', 'Dairy & Bakery', 'Meat & Seafood');
