-- Optimistic locking: detects concurrent modifications of the same product.
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
