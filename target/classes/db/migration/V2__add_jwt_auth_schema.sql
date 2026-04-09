ALTER TABLE users
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO roles (name, description, created_at)
SELECT 'ADMIN', 'System administrator', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'ADMIN'
);

INSERT INTO roles (name, description, created_at)
SELECT 'USER', 'Store operator', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'USER'
);

ALTER TABLE users
    ADD COLUMN role_id BIGINT;

UPDATE users
SET role_id = COALESCE(
        (SELECT id FROM roles WHERE name = users.role),
        (SELECT id FROM roles WHERE name = 'USER')
    )
WHERE role_id IS NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_role
        FOREIGN KEY (role_id) REFERENCES roles(id);

ALTER TABLE users
    ALTER COLUMN role_id SET NOT NULL;

ALTER TABLE users
    DROP COLUMN role;
