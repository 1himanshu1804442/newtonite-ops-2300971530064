-- ====================================================================
-- Newtonite Ops - Complete PostgreSQL Seed Data
-- ====================================================================

-- 1. Insert 4 Teams
INSERT INTO teams (id, name, code, description, created_at) VALUES
('11111111-1111-1111-1111-111111111111', 'Platform Engineering', 'ENG-PLAT', 'Core infrastructure, CI/CD pipelines, and internal developer platform services.', NOW()),
('22222222-2222-2222-2222-222222222222', 'Site Reliability Engineering', 'SRE-CORE', '24/7 reliability, latency optimization, SLO tracking, and critical incident response.', NOW()),
('33333333-3333-3333-3333-333333333333', 'Security Operations', 'SEC-OPS', 'Vulnerability scanning, access control, audit compliance, and threat mitigation.', NOW()),
('44444444-4444-4444-4444-444444444444', 'Data Infrastructure', 'DATA-INFRA', 'Streaming pipelines, Kafka clusters, lakehouse storage, and analytics compute engines.', NOW())
ON CONFLICT (code) DO NOTHING;

-- 2. Insert 12 Users
INSERT INTO users (id, name, email, avatar_url, created_at) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Alex Rivera', 'alex.rivera@newtonite.io', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150', NOW()),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Elena Rostova', 'elena.rostova@newtonite.io', 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=150', NOW()),
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Marcus Chen', 'marcus.chen@newtonite.io', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150', NOW()),
('dddddddd-dddd-dddd-dddd-dddddddddddd', 'Sarah Connor', 'sarah.connor@newtonite.io', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150', NOW()),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'Vikram Patel', 'vikram.patel@newtonite.io', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150', NOW()),
('ffffffff-ffff-ffff-ffff-ffffffffffff', 'Chloe Martin', 'chloe.martin@newtonite.io', 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150', NOW()),
('11112222-3333-4444-5555-666677778888', 'David Kim', 'david.kim@newtonite.io', 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150', NOW()),
('22223333-4444-5555-6666-777788889999', 'Priya Sharma', 'priya.sharma@newtonite.io', 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150', NOW()),
('33334444-5555-6666-7777-888899990000', 'James Wilson', 'james.wilson@newtonite.io', 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150', NOW()),
('44445555-6666-7777-8888-999900001111', 'Aisha Diallo', 'aisha.diallo@newtonite.io', 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150', NOW()),
('55556666-7777-8888-9999-000011112222', 'Liam O''Connor', 'liam.oconnor@newtonite.io', 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150', NOW()),
('66667777-8888-9999-0000-111122223333', 'Zoe Takahashi', 'zoe.takahashi@newtonite.io', 'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150', NOW())
ON CONFLICT (email) DO NOTHING;

-- 3. Team Memberships
INSERT INTO team_memberships (id, user_id, team_id, role, created_at) VALUES
(gen_random_uuid(), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'ADMIN', NOW()),
(gen_random_uuid(), 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111', 'LEAD', NOW()),
(gen_random_uuid(), 'cccccccc-cccc-cccc-cccc-cccccccccccc', '11111111-1111-1111-1111-111111111111', 'MEMBER', NOW()),
(gen_random_uuid(), 'dddddddd-dddd-dddd-dddd-dddddddddddd', '22222222-2222-2222-2222-222222222222', 'ADMIN', NOW()),
(gen_random_uuid(), 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '22222222-2222-2222-2222-222222222222', 'LEAD', NOW()),
(gen_random_uuid(), 'ffffffff-ffff-ffff-ffff-ffffffffffff', '22222222-2222-2222-2222-222222222222', 'MEMBER', NOW()),
(gen_random_uuid(), '11112222-3333-4444-5555-666677778888', '33333333-3333-3333-3333-333333333333', 'ADMIN', NOW()),
(gen_random_uuid(), '22223333-4444-5555-6666-777788889999', '33333333-3333-3333-3333-333333333333', 'LEAD', NOW()),
(gen_random_uuid(), '33334444-5555-6666-7777-888899990000', '33333333-3333-3333-3333-333333333333', 'VIEWER', NOW()),
(gen_random_uuid(), '44445555-6666-7777-8888-999900001111', '44444444-4444-4444-4444-444444444444', 'ADMIN', NOW()),
(gen_random_uuid(), '55556666-7777-8888-9999-000011112222', '44444444-4444-4444-4444-444444444444', 'LEAD', NOW()),
(gen_random_uuid(), '66667777-8888-9999-0000-111122223333', '44444444-4444-4444-4444-444444444444', 'MEMBER', NOW())
ON CONFLICT DO NOTHING;
