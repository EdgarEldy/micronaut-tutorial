-- V2: baseline RBAC data. Without it nobody could ever hold ROLE:WRITE or PERMISSION:WRITE to create
-- the first assignment. 7 resources x 2 actions, all granted to the ADMIN role.
-- Idempotent (ON CONFLICT DO NOTHING) so it stays safe if the rows were created by hand beforehand.

INSERT INTO permissions (resource, action)
SELECT r.resource, a.action
FROM (VALUES ('USER'), ('ROLE'), ('PERMISSION'), ('CATEGORY'), ('PRODUCT'), ('CUSTOMER'), ('ORDER')) AS r (resource)
CROSS JOIN (VALUES ('READ'), ('WRITE')) AS a (action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO roles (role_name) VALUES ('ADMIN') ON CONFLICT (role_name) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'ADMIN'
  AND p.resource IN ('USER', 'ROLE', 'PERMISSION', 'CATEGORY', 'PRODUCT', 'CUSTOMER', 'ORDER')
  AND p.action IN ('READ', 'WRITE')
ON CONFLICT (role_id, permission_id) DO NOTHING;
