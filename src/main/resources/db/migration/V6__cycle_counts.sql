CREATE TABLE cycle_counts (
    id BIGSERIAL PRIMARY KEY,
    count_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    location_id BIGINT NOT NULL REFERENCES locations(id),
    created_by BIGINT NOT NULL REFERENCES users(id),
    completed_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE cycle_count_items (
    id BIGSERIAL PRIMARY KEY,
    cycle_count_id BIGINT NOT NULL REFERENCES cycle_counts(id) ON DELETE CASCADE,
    inventory_id BIGINT NOT NULL REFERENCES inventory(id),
    expected_quantity INTEGER NOT NULL,
    actual_quantity INTEGER,
    variance INTEGER,
    counted_by BIGINT REFERENCES users(id),
    counted_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    CONSTRAINT uk_cycle_count_items_cycle_inventory UNIQUE (cycle_count_id, inventory_id)
);

CREATE INDEX idx_cycle_counts_status ON cycle_counts(status);
CREATE INDEX idx_cycle_counts_location_id ON cycle_counts(location_id);
CREATE INDEX idx_cycle_count_items_cycle_count_id ON cycle_count_items(cycle_count_id);
CREATE INDEX idx_cycle_count_items_inventory_id ON cycle_count_items(inventory_id);
