CREATE SCHEMA IF NOT EXISTS analytics;

INSERT INTO permissions (code, description) VALUES
('AI_RECOMMENDATION_READ', 'Read deterministic AI reorder recommendations'),
('AI_RECOMMENDATION_APPROVE', 'Approve AI reorder recommendations into draft purchase orders')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('AI_RECOMMENDATION_READ', 'AI_RECOMMENDATION_APPROVE')
WHERE r.name IN ('ADMIN', 'MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('AI_RECOMMENDATION_READ')
WHERE r.name IN ('USER', 'STAFF')
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE analytics.ai_forecast_snapshots (
    id BIGSERIAL PRIMARY KEY,
    business_date DATE NOT NULL,
    forecast_start_date DATE NOT NULL,
    forecast_end_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    center_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    trailing_seven_day_average NUMERIC(12, 2) NOT NULL DEFAULT 0,
    same_weekday_average NUMERIC(12, 2) NOT NULL DEFAULT 0,
    weighted_daily_demand NUMERIC(12, 2) NOT NULL DEFAULT 0,
    seven_day_forecast_quantity INTEGER NOT NULL DEFAULT 0,
    lead_time_days INTEGER NOT NULL DEFAULT 1,
    lead_time_demand_quantity INTEGER NOT NULL DEFAULT 0,
    history_days_considered INTEGER NOT NULL DEFAULT 0,
    demand_event_count INTEGER NOT NULL DEFAULT 0,
    insufficient_history BOOLEAN NOT NULL DEFAULT FALSE,
    explanation_summary VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ai_forecast_snapshot_scope_date UNIQUE (business_date, product_id, center_id, warehouse_id)
);

CREATE INDEX idx_ai_forecast_snapshot_lookup
    ON analytics.ai_forecast_snapshots (business_date, center_id, warehouse_id, product_id);

CREATE TABLE analytics.ai_reorder_recommendations (
    id BIGSERIAL PRIMARY KEY,
    business_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    center_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    forecast_snapshot_id BIGINT NOT NULL REFERENCES analytics.ai_forecast_snapshots(id) ON DELETE CASCADE,
    current_stock_quantity INTEGER NOT NULL DEFAULT 0,
    safety_stock_quantity INTEGER NOT NULL DEFAULT 0,
    recommended_quantity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    explanation_summary VARCHAR(500),
    approved_purchase_order_id BIGINT REFERENCES purchase_orders(id),
    approved_by_user_id BIGINT REFERENCES users(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ai_recommendation_scope_date UNIQUE (business_date, product_id, center_id, warehouse_id),
    CONSTRAINT uk_ai_recommendation_forecast UNIQUE (forecast_snapshot_id)
);

CREATE INDEX idx_ai_recommendation_lookup
    ON analytics.ai_reorder_recommendations (business_date, center_id, warehouse_id, product_id, status);
