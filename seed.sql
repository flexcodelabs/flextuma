INSERT INTO privilege (id, name, value, system, active, created, updated)
VALUES ('5269df21-c8a0-4776-bd89-1015521bc19d', 'Super Admin', 'SUPER_ADMIN', true, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO role (id, name, system, active, created, updated)
VALUES ('6269df23-f8a0-4776-bd89-3015521bc19d', 'Super Admin', true, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO userprivilege (role, privilege)
VALUES ('6269df23-f8a0-4776-bd89-3015521bc19d', '5269df21-c8a0-4776-bd89-1015521bc19d')
ON CONFLICT DO NOTHING;

INSERT INTO "user" (id, username, name, email, phonenumber, password, type, active, verified, system, created, updated)
VALUES ('6269df23-f8a0-4776-bd89-3015521bc19d', 'admin', 'ADMIN', 'admin@flextuma.com', '123456789', '$2a$10$7P8p.0K1iUInX9O0Xo6S/.8L.y0W5K1f6TqN9Y8I0X.hU5y8kY6u.', 'SYSTEM', true, true, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO "user" (id, username, name, email, phonenumber, password, type, active, verified, system, created, updated)
VALUES ('5269df21-c8a0-4776-bd89-1015521bc19d', 'SYSTEM', 'SYSTEM', 'system@flextuma.com', '0000000000', '$2a$10$7P8p.0K1iUInX9O0Xo6S/.8L.y0W5K1f6TqN9Y8I0X.hU5y8kY6u.', 'SYSTEM', true, true, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO userrole (owner, role) VALUES ('6269df23-f8a0-4776-bd89-3015521bc19d', '6269df23-f8a0-4776-bd89-3015521bc19d') ON CONFLICT DO NOTHING;
INSERT INTO userrole (owner, role) VALUES ('5269df21-c8a0-4776-bd89-1015521bc19d', '6269df23-f8a0-4776-bd89-3015521bc19d') ON CONFLICT DO NOTHING;