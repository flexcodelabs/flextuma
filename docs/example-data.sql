-- Example Data for Flextuma

-- 1. Example Organisation
INSERT INTO organisation (id, name, active, code) 
VALUES (gen_random_uuid(), 'Flex Code Labs', true, 'FLEX01');

-- 2. Example SMS Connector (Beem)
INSERT INTO smsconnector (id, name, provider, apiKey, secretKey, active)
VALUES (gen_random_uuid(), 'Main Beem Account', 'BEEM', 'your_beem_api_key', 'your_beem_secret', true);

-- 3. Example SMS Template
INSERT INTO smstemplate (id, name, code, content, active, system)
VALUES (gen_random_uuid(), 'Welcome SMS', 'WELCOME_SMS', 'Hello {{name}}, welcome to Flextuma!', true, true);

-- 4. Admin User (if not exists)
-- Note: Password hashing is usually handled at runtime, so use the /api/users endpoint ideally.
