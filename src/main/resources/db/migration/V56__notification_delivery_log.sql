-- Append-only audit of webhook notification deliveries: what message (event/severity/title) was
-- sent where (masked webhook target), with what guidance source, and the outcome (SENT/FAILED/SKIPPED).
-- The webhook URL is stored masked (host + hash) so the signed token is never persisted in plaintext.

CREATE TABLE notification_delivery_log (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(100),
    alert_type VARCHAR(100),
    severity VARCHAR(30),
    provider VARCHAR(30),
    webhook_target VARCHAR(255),
    title VARCHAR(255),
    message TEXT,
    guidance_source VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notification_delivery_log_created_at ON notification_delivery_log (created_at);
CREATE INDEX idx_notification_delivery_log_event_type ON notification_delivery_log (event_type);
