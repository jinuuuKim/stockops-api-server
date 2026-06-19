-- Throttle state for low-stock Teams webhook notifications.
-- One row per alert scope ("WAREHOUSE:<id>" or "GLOBAL") records when that scope was last
-- notified, so the scheduled scan sends at most one card per scope per cooldown window and the
-- low-stock channel never spams (in-app NotificationBell remains real-time and is unaffected).

CREATE TABLE low_stock_alert_state (
    id               BIGSERIAL                  PRIMARY KEY,
    scope_key        VARCHAR(100)               NOT NULL UNIQUE,
    last_notified_at TIMESTAMP WITH TIME ZONE   NOT NULL,
    last_low_count   INTEGER                    NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE   NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE   NOT NULL
);
