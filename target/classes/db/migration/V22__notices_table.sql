-- Notices table for admin announcements
CREATE TABLE notices (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    type VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
    active BOOLEAN NOT NULL DEFAULT true,
    created_by BIGINT,
    notice_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notices_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX idx_notices_active ON notices(active);
CREATE INDEX idx_notices_type ON notices(type);
CREATE INDEX idx_notices_notice_at ON notices(notice_at);