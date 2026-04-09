CREATE TABLE cycle_counts (
    id BIGSERIAL PRIMARY KEY,
    count_date DATE NOT NULL DEFAULT CURRENT_DATE,
    notes TEXT,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE cycle_count_items (
    id BIGSERIAL PRIMARY KEY,
    cycle_count_id BIGINT NOT NULL REFERENCES cycle_counts(id),
    inventory_id BIGINT NOT NULL REFERENCES inventory(id),
    expected_quantity INTEGER NOT NULL,
    actual_quantity INTEGER,
    variance INTEGER,
    notes TEXT,
    counted_by BIGINT REFERENCES users(id),
    counted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
