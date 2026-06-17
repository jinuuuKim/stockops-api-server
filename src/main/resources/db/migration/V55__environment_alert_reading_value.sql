-- Persist the measured value that triggered an environment alert so the notification can show
-- "현재 값" and derive "상태" (초과/미달) at dispatch time. Alerts are dispatched asynchronously by
-- the outbox sender from the persisted row, so the value must live on the alert, not only at
-- ingestion time. Nullable: door/system events carry no numeric reading; existing rows stay null.

ALTER TABLE environment_alerts ADD COLUMN reading_value DOUBLE PRECISION;
ALTER TABLE environment_alerts ADD COLUMN reading_unit VARCHAR(30);
