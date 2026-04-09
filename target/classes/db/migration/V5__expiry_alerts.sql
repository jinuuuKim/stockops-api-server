CREATE TABLE expiry_alerts (
    id BIGSERIAL PRIMARY KEY,
    lot_id BIGINT NOT NULL REFERENCES lots(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    days_until_expiry INTEGER NOT NULL,
    alert_level VARCHAR(20) NOT NULL,
    expiry_date DATE NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    is_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_expiry_alerts_acknowledged ON expiry_alerts(is_acknowledged);
CREATE INDEX idx_expiry_alerts_level ON expiry_alerts(alert_level);
CREATE INDEX idx_expiry_alerts_product ON expiry_alerts(product_id);
