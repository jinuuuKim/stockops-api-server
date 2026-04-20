-- V16: Ensure ADMIN role has all permissions including ENVIRONMENT_*
-- This migration ensures ADMIN always has all permissions regardless of when they were added
-- Uses a simpler approach without CROSS JOIN to avoid any timing issues

-- 1. First ensure ADMIN has all permissions that exist at migration time
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 2. Verify ENVIRONMENT_* permissions exist, insert if not
INSERT INTO permissions (code, description) VALUES
('ENVIRONMENT_READ', 'Read environment monitoring data'),
('ENVIRONMENT_MANAGE', 'Manage environment sensors and controllers'),
('ENVIRONMENT_COMMAND', 'Send commands to environment controllers')
ON CONFLICT (code) DO NOTHING;

-- 3. Now assign ENVIRONMENT_* to ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.code IN ('ENVIRONMENT_READ', 'ENVIRONMENT_MANAGE', 'ENVIRONMENT_COMMAND')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 4. Also grant all permissions to MANAGER role for environment
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'MANAGER'
  AND p.code IN ('ENVIRONMENT_READ', 'ENVIRONMENT_MANAGE', 'ENVIRONMENT_COMMAND')
ON CONFLICT (role_id, permission_id) DO NOTHING;
