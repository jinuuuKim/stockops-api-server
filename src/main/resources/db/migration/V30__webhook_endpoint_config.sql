-- V30: Webhook endpoint configuration table
-- Stores per-center/warehouse webhook endpoints with provider type and extra config

CREATE TABLE webhook_endpoint_config (
    id              BIGSERIAL       PRIMARY KEY,
    center_id       BIGINT          REFERENCES centers(id) ON DELETE SET NULL,
    warehouse_id    BIGINT          REFERENCES warehouses(id) ON DELETE SET NULL,
    provider_type   VARCHAR(20)     NOT NULL,
    webhook_url     VARCHAR(2048)   NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    extra_config    TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_config_center ON webhook_endpoint_config (center_id);
CREATE INDEX idx_webhook_config_warehouse ON webhook_endpoint_config (warehouse_id);
CREATE INDEX idx_webhook_config_provider ON webhook_endpoint_config (provider_type);
CREATE INDEX idx_webhook_config_enabled ON webhook_endpoint_config (enabled) WHERE enabled = TRUE;