CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.daily_demand_history (
    id BIGSERIAL PRIMARY KEY,
    business_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    center_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    confirmed_outbound_quantity INTEGER NOT NULL DEFAULT 0,
    confirmed_outbound_event_count INTEGER NOT NULL DEFAULT 0,
    insufficient_history BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_analytics_daily_demand UNIQUE (business_date, product_id, center_id, warehouse_id)
);

CREATE INDEX idx_analytics_daily_demand_lookup
    ON analytics.daily_demand_history (business_date, center_id, warehouse_id, product_id);

CREATE TABLE analytics.daily_stock_position (
    id BIGSERIAL PRIMARY KEY,
    business_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    center_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    on_hand_quantity INTEGER NOT NULL DEFAULT 0,
    available_quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    quarantined_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_analytics_daily_stock UNIQUE (business_date, product_id, center_id, warehouse_id)
);

CREATE INDEX idx_analytics_daily_stock_lookup
    ON analytics.daily_stock_position (business_date, center_id, warehouse_id, product_id);

CREATE TABLE analytics.daily_expiry_waste (
    id BIGSERIAL PRIMARY KEY,
    business_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    center_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quarantined_quantity INTEGER NOT NULL DEFAULT 0,
    quarantined_lot_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_analytics_daily_expiry_waste UNIQUE (business_date, product_id, center_id, warehouse_id)
);

CREATE INDEX idx_analytics_daily_expiry_waste_lookup
    ON analytics.daily_expiry_waste (business_date, center_id, warehouse_id, product_id);

CREATE TABLE analytics.daily_purchase_order_lead_time (
    id BIGSERIAL PRIMARY KEY,
    business_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    center_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    purchase_order_count INTEGER NOT NULL DEFAULT 0,
    lead_time_sample_count INTEGER NOT NULL DEFAULT 0,
    total_lead_time_hours BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_analytics_daily_po_lead_time UNIQUE (business_date, product_id, center_id, warehouse_id)
);

CREATE INDEX idx_analytics_daily_po_lead_time_lookup
    ON analytics.daily_purchase_order_lead_time (business_date, center_id, warehouse_id, product_id);

CREATE TABLE analytics.daily_fill_rate_source (
    id BIGSERIAL PRIMARY KEY,
    business_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    center_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    purchase_order_count INTEGER NOT NULL DEFAULT 0,
    requested_quantity INTEGER NOT NULL DEFAULT 0,
    accepted_quantity INTEGER NOT NULL DEFAULT 0,
    cancelled_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_analytics_daily_fill_rate_source UNIQUE (business_date, product_id, center_id, warehouse_id)
);

CREATE INDEX idx_analytics_daily_fill_rate_source_lookup
    ON analytics.daily_fill_rate_source (business_date, center_id, warehouse_id, product_id);
