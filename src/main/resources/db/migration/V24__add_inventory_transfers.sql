-- Inventory transfers table for atomic stock movement between locations
CREATE TABLE inventory_transfers (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    lot_id BIGINT,
    from_location_id BIGINT NOT NULL,
    to_location_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'REQUESTED',
    requested_by BIGINT REFERENCES users(id),
    completed_by BIGINT REFERENCES users(id),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_transfers_status ON inventory_transfers(status);
CREATE INDEX idx_inventory_transfers_from_location ON inventory_transfers(from_location_id);
CREATE INDEX idx_inventory_transfers_to_location ON inventory_transfers(to_location_id);
CREATE INDEX idx_inventory_transfers_product ON inventory_transfers(product_id);
