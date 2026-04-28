-- V28: Pending Alerts and User Phone
-- 대기 중인 환경 알림 추적 및 사용자 전화번호 추가
-- 에스컬레이션 스케줄러가 DB에서 PENDING 알림을 조회하여 자동 에스컬레이션

-- ============================================
-- 1. PENDING_ALERTS TABLE
-- ============================================
CREATE TABLE pending_alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    center_id BIGINT NOT NULL,
    warehouse_id BIGINT,
    sensor_id BIGINT,
    message TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    current_level INTEGER NOT NULL DEFAULT 0,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    acknowledged_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pending_alerts_center FOREIGN KEY (center_id) REFERENCES centers(id),
    CONSTRAINT fk_pending_alerts_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);

COMMENT ON TABLE pending_alerts IS '대기 중인 환경 알림 - 에스컬레이션 대기/진행/확인 상태 추적';
COMMENT ON COLUMN pending_alerts.id IS '대기 알림 PK';
COMMENT ON COLUMN pending_alerts.alert_type IS '알림 유형 (TEMPERATURE, HUMIDITY, AIR_QUALITY 등)';
COMMENT ON COLUMN pending_alerts.center_id IS '센터 ID (필수)';
COMMENT ON COLUMN pending_alerts.warehouse_id IS '창고 ID (NULL이면 센터 전체 알림)';
COMMENT ON COLUMN pending_alerts.sensor_id IS '센서 ID (NULL 가능)';
COMMENT ON COLUMN pending_alerts.message IS '알림 메시지';
COMMENT ON COLUMN pending_alerts.severity IS '심각도 (WARNING, CRITICAL 등)';
COMMENT ON COLUMN pending_alerts.status IS '상태 (PENDING, ESCALATED, ACKNOWLEDGED)';
COMMENT ON COLUMN pending_alerts.current_level IS '현재 에스컬레이션 레벨 (0=초기)';
COMMENT ON COLUMN pending_alerts.acknowledged_at IS '확인 시각 (UTC)';
COMMENT ON COLUMN pending_alerts.acknowledged_by IS '확인자 사용자명';
COMMENT ON COLUMN pending_alerts.created_at IS '생성 시각 (UTC 기준 저장)';
COMMENT ON COLUMN pending_alerts.updated_at IS '수정 시각 (UTC 기준 저장)';

CREATE INDEX IF NOT EXISTS idx_pending_alerts_status
    ON pending_alerts (status);

CREATE INDEX IF NOT EXISTS idx_pending_alerts_status_created
    ON pending_alerts (status, created_at);

-- ============================================
-- 2. ADD PHONE COLUMN TO USERS TABLE
-- ============================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(50);

COMMENT ON COLUMN users.phone IS '사용자 전화번호 (E.164 형식, SMS 알림용)';