CREATE TABLE role_scope_assignments (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    scope_type VARCHAR(20) NOT NULL,
    center_id BIGINT REFERENCES centers(id),
    warehouse_id BIGINT REFERENCES warehouses(id),
    CONSTRAINT chk_role_scope_assignment_type CHECK (
        (scope_type = 'GLOBAL' AND center_id IS NULL AND warehouse_id IS NULL)
            OR (scope_type = 'CENTER' AND center_id IS NOT NULL AND warehouse_id IS NULL)
            OR (scope_type = 'WAREHOUSE' AND warehouse_id IS NOT NULL)
        )
);

CREATE INDEX idx_role_scope_assignments_center_id ON role_scope_assignments(center_id);
CREATE INDEX idx_role_scope_assignments_warehouse_id ON role_scope_assignments(warehouse_id);

CREATE TABLE user_scope_assignments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope_type VARCHAR(20) NOT NULL,
    center_id BIGINT REFERENCES centers(id),
    warehouse_id BIGINT REFERENCES warehouses(id),
    CONSTRAINT chk_user_scope_assignment_type CHECK (
        (scope_type = 'GLOBAL' AND center_id IS NULL AND warehouse_id IS NULL)
            OR (scope_type = 'CENTER' AND center_id IS NOT NULL AND warehouse_id IS NULL)
            OR (scope_type = 'WAREHOUSE' AND warehouse_id IS NOT NULL)
        )
);

CREATE INDEX idx_user_scope_assignments_center_id ON user_scope_assignments(center_id);
CREATE INDEX idx_user_scope_assignments_warehouse_id ON user_scope_assignments(warehouse_id);
