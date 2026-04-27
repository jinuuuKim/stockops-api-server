-- V25: Add warehouse closure tracking columns
ALTER TABLE warehouses
    ADD COLUMN closure_reason TEXT,
    ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN warehouses.closure_reason IS '창고 폐쇄 사유';
COMMENT ON COLUMN warehouses.closed_at IS '창고 폐쇄 일시';
