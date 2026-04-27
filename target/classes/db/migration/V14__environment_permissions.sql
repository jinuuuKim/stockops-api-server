-- V14: Environment Monitoring Permissions
-- 환경 모니터링 관련 권한 추가 및 역할 할당

-- ============================================
-- 1. ENVIRONMENT PERMISSIONS
-- ============================================
INSERT INTO permissions (code, description) VALUES
('ENVIRONMENT_READ', 'Read environment monitoring data (sensors, controllers, dashboard, alerts, history)'),
('ENVIRONMENT_MANAGE', 'Manage environment sensors and controllers (create, update, delete)'),
('ENVIRONMENT_COMMAND', 'Send commands to environment controllers')
ON CONFLICT (code) DO NOTHING;

-- ============================================
-- 2. ADMIN ROLE GETS ALL ENVIRONMENT PERMISSIONS
-- ============================================
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.code IN ('ENVIRONMENT_READ', 'ENVIRONMENT_MANAGE', 'ENVIRONMENT_COMMAND')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================
-- 3. MANAGER ROLE GETS READ + MANAGE + COMMAND
-- ============================================
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'ENVIRONMENT_READ',
    'ENVIRONMENT_MANAGE',
    'ENVIRONMENT_COMMAND'
)
WHERE r.name = 'MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================
-- 4. USER/STAFF ROLE GETS READ ONLY
-- ============================================
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('ENVIRONMENT_READ')
WHERE r.name IN ('USER', 'STAFF')
ON CONFLICT (role_id, permission_id) DO NOTHING;
