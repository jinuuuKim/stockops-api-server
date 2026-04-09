ALTER TABLE inventory
    ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE inventory
    ADD CONSTRAINT uk_inventory_product_location_lot UNIQUE (product_id, location_id, lot_id);
