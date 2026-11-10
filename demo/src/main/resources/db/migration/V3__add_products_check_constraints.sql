-- Last line of defence for the business rules the Product aggregate already enforces
-- (non-blank name, price > 0, stock >= 0), so no writer that bypasses the application
-- (manual SQL, scripts, another service) can store an invalid product.
--
-- Note: this migration fails if existing rows already violate a rule; fix or remove those rows first.
ALTER TABLE products
    ADD CONSTRAINT chk_products_name_not_blank CHECK (btrim(name) <> ''),
    ADD CONSTRAINT chk_products_price_positive CHECK (price > 0),
    ADD CONSTRAINT chk_products_stock_non_negative CHECK (stock_quantity >= 0);
