-- RBAC overhaul to a six-role model + Store entity.
-- ADMIN stays the super administrator (keeps the ROLE_ADMIN all-grant special case, minimal
-- security-core churn). MANAGER/STAFF are repurposed as store roles (client-web already expects
-- STORE_MANAGER/STORE_STAFF). Three new roles are added. Legacy USER role is left untouched.
--
-- Role meanings:
--   ADMIN            최고관리자  — all permissions
--   GENERAL_ADMIN    일반관리자  — all except super-admin-only (user/role management)
--   CENTER_MANAGER   센터 관리자 — own-center ops + warehouse registration
--   WAREHOUSE_MANAGER 창고 관리자 — own-warehouse ops, no warehouse registration
--   STORE_MANAGER    지점매니저  — client-web: global read + create/cancel own-store orders
--   STORE_STAFF      지점직원    — client-web: global read + create orders

-- 1) New store-management permissions
INSERT INTO permissions (code, description)
SELECT source.code, source.description
FROM (VALUES
    ('STORE_CREATE', 'Create stores'),
    ('STORE_READ', 'Read stores'),
    ('STORE_UPDATE', 'Update stores'),
    ('STORE_DELETE', 'Delete stores')
) AS source (code, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions target WHERE target.code = source.code);

-- 2) Rename existing roles to their store meanings (IDs/FKs preserved)
UPDATE roles SET name = 'STORE_MANAGER', description = '지점매니저' WHERE name = 'MANAGER';
UPDATE roles SET name = 'STORE_STAFF', description = '지점직원' WHERE name = 'STAFF';

-- Old broad grants no longer fit the store roles — clear and re-grant a minimal set
DELETE FROM role_permissions
WHERE role_id IN (SELECT id FROM roles WHERE name IN ('STORE_MANAGER', 'STORE_STAFF'));

-- 3) New roles
INSERT INTO roles (name, description, created_at)
SELECT 'GENERAL_ADMIN', '일반관리자', NOW()
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'GENERAL_ADMIN');

INSERT INTO roles (name, description, created_at)
SELECT 'CENTER_MANAGER', '센터 관리자', NOW()
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'CENTER_MANAGER');

INSERT INTO roles (name, description, created_at)
SELECT 'WAREHOUSE_MANAGER', '창고 관리자', NOW()
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'WAREHOUSE_MANAGER');

-- 4) ADMIN keeps every permission (covers newly added STORE_* codes too)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND NOT EXISTS (SELECT 1 FROM role_permissions e WHERE e.role_id = r.id AND e.permission_id = p.id);

-- 5) GENERAL_ADMIN: all permissions except super-admin-only user/role management
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'GENERAL_ADMIN'
AND p.code NOT IN ('USER_CREATE', 'USER_DELETE', 'ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_DELETE')
AND NOT EXISTS (SELECT 1 FROM role_permissions e WHERE e.role_id = r.id AND e.permission_id = p.id);

-- 6) CENTER_MANAGER: own-center settings, warehouse registration, PO + inbound/outbound
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'CENTER_READ', 'WAREHOUSE_CREATE', 'WAREHOUSE_READ', 'WAREHOUSE_UPDATE',
    'LOCATION_CREATE', 'LOCATION_READ', 'LOCATION_UPDATE',
    'PRODUCT_READ', 'INVENTORY_READ',
    'INVENTORY_ADJUST_CREATE', 'INVENTORY_ADJUST_READ', 'INVENTORY_ADJUST_APPROVE',
    'INBOUND_CREATE', 'INBOUND_READ', 'INBOUND_CONFIRM',
    'OUTBOUND_CREATE', 'OUTBOUND_READ', 'OUTBOUND_CONFIRM',
    'PURCHASE_ORDER_CREATE', 'PURCHASE_ORDER_READ', 'PURCHASE_ORDER_MANAGE',
    'CYCLE_COUNT_CREATE', 'CYCLE_COUNT_EXECUTE', 'CYCLE_COUNT_READ',
    'EXPIRY_ALERT_READ', 'EXPIRY_ALERT_MANAGE', 'DASHBOARD_READ',
    'REASON_CODE_READ', 'ENVIRONMENT_READ', 'STORE_READ'
)
WHERE r.name = 'CENTER_MANAGER'
AND NOT EXISTS (SELECT 1 FROM role_permissions e WHERE e.role_id = r.id AND e.permission_id = p.id);

-- 7) WAREHOUSE_MANAGER: own-warehouse ops, no warehouse registration
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'WAREHOUSE_READ', 'LOCATION_READ', 'PRODUCT_READ', 'INVENTORY_READ',
    'INVENTORY_ADJUST_CREATE', 'INVENTORY_ADJUST_READ',
    'INBOUND_CREATE', 'INBOUND_READ', 'INBOUND_CONFIRM',
    'OUTBOUND_CREATE', 'OUTBOUND_READ', 'OUTBOUND_CONFIRM',
    'PURCHASE_ORDER_READ', 'CYCLE_COUNT_READ', 'CYCLE_COUNT_EXECUTE',
    'EXPIRY_ALERT_READ', 'DASHBOARD_READ', 'ENVIRONMENT_READ'
)
WHERE r.name = 'WAREHOUSE_MANAGER'
AND NOT EXISTS (SELECT 1 FROM role_permissions e WHERE e.role_id = r.id AND e.permission_id = p.id);

-- 8) STORE_MANAGER / STORE_STAFF: client-web only — global read + create orders
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'INVENTORY_READ', 'PRODUCT_READ', 'PURCHASE_ORDER_READ', 'PURCHASE_ORDER_CREATE', 'DASHBOARD_READ'
)
WHERE r.name = 'STORE_MANAGER'
AND NOT EXISTS (SELECT 1 FROM role_permissions e WHERE e.role_id = r.id AND e.permission_id = p.id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'INVENTORY_READ', 'PRODUCT_READ', 'PURCHASE_ORDER_READ', 'PURCHASE_ORDER_CREATE'
)
WHERE r.name = 'STORE_STAFF'
AND NOT EXISTS (SELECT 1 FROM role_permissions e WHERE e.role_id = r.id AND e.permission_id = p.id);

-- 9) Store master table (retail branch that originates purchase requests)
CREATE TABLE stores (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    contact VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 10) Store membership for store users (nullable; only store roles set it)
ALTER TABLE users ADD COLUMN store_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_users_store FOREIGN KEY (store_id) REFERENCES stores(id);
