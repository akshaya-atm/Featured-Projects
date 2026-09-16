-- Marks a saved address as the default; application logic (AddressRepository) keeps this to exactly one TRUE row per user.
ALTER TABLE addresses ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: promote each user's most recently added address to default.
UPDATE addresses a
SET is_default = TRUE
WHERE a.address_id = (
    SELECT b.address_id FROM addresses b
    WHERE b.user_id = a.user_id
    ORDER BY b.address_id DESC
    LIMIT 1
);
