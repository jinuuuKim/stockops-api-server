CREATE TABLE analytics.ai_suggestion_audits (
    id BIGSERIAL PRIMARY KEY,
    suggestion_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    source_type VARCHAR(100) NOT NULL,
    approval_mode VARCHAR(100) NOT NULL,
    before_status VARCHAR(50),
    after_status VARCHAR(50),
    previous_status VARCHAR(50),
    next_status VARCHAR(50),
    before_payload_summary TEXT,
    after_payload_summary TEXT,
    actor_user_id BIGINT,
    actor_name VARCHAR(200),
    actor_role VARCHAR(100),
    target_type VARCHAR(100),
    target_id BIGINT,
    target_scope_type VARCHAR(50),
    target_scope_id BIGINT,
    request_id VARCHAR(100),
    result VARCHAR(50) NOT NULL,
    error_message TEXT,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_suggestion_audits_suggestion_id_recorded_at
    ON analytics.ai_suggestion_audits (suggestion_id, recorded_at);

CREATE INDEX idx_ai_suggestion_audits_action_recorded_at
    ON analytics.ai_suggestion_audits (action, recorded_at);
