-- V15: Add missing updated_at column to environment_alerts
-- BaseEntity requires updated_at but V13 migration didn't include it

ALTER TABLE environment_alerts
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
