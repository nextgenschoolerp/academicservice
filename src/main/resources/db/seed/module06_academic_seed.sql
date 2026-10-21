-- =====================================================================================================
-- Module 06 (academicservice) seed: Delhi Public School North, 2026-2027
-- =====================================================================================================
-- Run after the Module 01 and Module 03 seeds, and after academicservice has started once
-- (Hibernate ddl-auto creates subjects, bell_schedules, master_timetables, … ):
--     powershell -ExecutionPolicy Bypass -File .agents/skills/test-ui/scripts/db/db.ps1 -Script <this file>
--
-- Adds:
--   * roles ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN and one DPS North account each
--     (coordinator_sunita, timetable_rahul, substitution_meenal, elearning_farah; users 5eed0001-0001-4000-8000-000000000036 … 39)
--   * four subject teachers (SUBJECT_TEACHER): teacher_kavya (ENG), teacher_imran (SST), teacher_deepa (SCI), teacher_arjun (HIN)
--     (users …040 … 043). Also timetabled: subjectteacher_neha (MATH, SCI) and classteacher_priya (ENG, HIN) from earlier seeds;
--     teacher_sana (…044, MATH, SCI) has no classes, so she is the natural proxy for Mathematics and Science.
--   All accounts: password Password@123 (documented test hash).
--
-- Story coverage:
--   US-06000 bell schedule "DPS North regular" (Mon-Sat): assembly, periods 1-3, recess, periods 4-6, lunch, periods 7-8
--            (8 teaching periods a day, 48 a week); active for DPS-DELHI-NORTH
--   US-06001 subjects ENG HIN MATH SCI SST CS; workload caps for the 7 teachers (30 a week, 6 a day; teacher_arjun 20 / 5);
--            allocations for Class 6-A and 6-C (ENG 7, HIN 5, MATH 7, SCI 6, SST 6 = 31 periods each)
--   US-06002 no timetable rows: generate them with POST /api/v1/lms/timetables/generate (6-A, 6-C) as timetable_rahul
--   US-06004 Class 6 Social Science plan for 2026-2027 (6 chapters, April to January); 6-A has taught chapters 1-3,
--            6-C only chapter 1, so 6-C shows BEHIND on 19 Oct 2026
--   US-06011 Computer Lab 1 (40 seats) and Science Lab (24 seats)
--
-- Re-runnable: every row is marked created_by = 'seed:module06' and every seed UUID starts with 5eed0006-.
-- Step 0 also removes rows later added through the API that hang off seeded rows (timetables of the seeded
-- bell schedule and their substitutions, coverage of seeded chapters, reservations of seeded labs).
-- =====================================================================================================

BEGIN;

-- ---------------------------------------------------------------------------------------------------
-- 0. Clean previous seed rows (children first)
-- ---------------------------------------------------------------------------------------------------
DELETE FROM faculty_proxy_substitutions
 WHERE timetable_id IN (SELECT timetable_id FROM master_timetables WHERE bell_schedule_id::text LIKE '5eed0006-%');
DELETE FROM master_timetables   WHERE bell_schedule_id::text LIKE '5eed0006-%';
DELETE FROM faculty_absences    WHERE teacher_id IN (SELECT teacher_id FROM faculty_workloads WHERE workload_id::text LIKE '5eed0006-%');
DELETE FROM subject_allocations WHERE allocation_id::text LIKE '5eed0006-%' OR subject_id::text LIKE '5eed0006-%';
DELETE FROM faculty_workloads   WHERE workload_id::text LIKE '5eed0006-%';
DELETE FROM bell_periods        WHERE bell_schedule_id::text LIKE '5eed0006-%';
DELETE FROM bell_schedules      WHERE bell_schedule_id::text LIKE '5eed0006-%';
DELETE FROM syllabus_coverage   WHERE topic_id::text LIKE '5eed0006-%';
DELETE FROM syllabus_topics     WHERE topic_id::text LIKE '5eed0006-%';
DELETE FROM lab_reservations    WHERE room_id::text LIKE '5eed0006-%';
DELETE FROM lab_rooms           WHERE room_id::text LIKE '5eed0006-%';
DELETE FROM subjects            WHERE subject_id::text LIKE '5eed0006-%';
DELETE FROM user_roles          WHERE created_by = 'seed:module06';
DELETE FROM user_credentials    WHERE created_by = 'seed:module06';
DELETE FROM users               WHERE created_by = 'seed:module06';
DELETE FROM roles               WHERE created_by = 'seed:module06';

-- ---------------------------------------------------------------------------------------------------
-- 1. Module 06 roles and staff accounts
-- ---------------------------------------------------------------------------------------------------
INSERT INTO roles (name, description, active, created_at, updated_at, created_by, updated_by, version)
SELECT r.name, r.description, true, TIMESTAMPTZ '2026-03-20 04:30:00+00', TIMESTAMPTZ '2026-03-20 04:30:00+00', 'seed:module06', NULL, 0
FROM (VALUES
    ('ACADEMIC_COORDINATOR',  'Plans the syllabus, bell schedule and lab rooms; reviews lesson plans; archives closed years'),
    ('TIMETABLE_INCHARGE',    'Sets teacher workload caps and allocations and generates class timetables'),
    ('SUBSTITUTION_INCHARGE', 'Records morning absences and assigns proxy teachers'),
    ('ELEARNING_ADMIN',       'Uploads e-learning assets and authors LMS quizzes')
) AS r(name, description)
WHERE NOT EXISTS (SELECT 1 FROM roles x WHERE x.name = r.name);

CREATE TEMP TABLE seed_staff (user_id text, username text, first_name text, last_name text, email text, phone text, user_type text,
                              created_at timestamptz) ON COMMIT DROP;
INSERT INTO seed_staff VALUES
('5eed0001-0001-4000-8000-000000000036', 'coordinator_sunita',  'Sunita', 'Menon',   'sunita.menon@dps.edu.in',   '+919811100136', 'ACADEMIC_COORDINATOR',  '2026-03-20 05:00:00+00'),
('5eed0001-0001-4000-8000-000000000037', 'timetable_rahul',     'Rahul',  'Verma',   'rahul.verma@dps.edu.in',    '+919811100137', 'TIMETABLE_INCHARGE',    '2026-03-20 05:05:00+00'),
('5eed0001-0001-4000-8000-000000000038', 'substitution_meenal', 'Meenal', 'Joshi',   'meenal.joshi@dps.edu.in',   '+919811100138', 'SUBSTITUTION_INCHARGE', '2026-03-20 05:10:00+00'),
('5eed0001-0001-4000-8000-000000000039', 'elearning_farah',     'Farah',  'Khan',    'farah.khan@dps.edu.in',     '+919811100139', 'ELEARNING_ADMIN',       '2026-03-20 05:15:00+00'),
('5eed0001-0001-4000-8000-000000000040', 'teacher_kavya',       'Kavya',  'Pillai',  'kavya.pillai@dps.edu.in',   '+919811100140', 'SUBJECT_TEACHER',       '2026-03-20 05:20:00+00'),
('5eed0001-0001-4000-8000-000000000041', 'teacher_imran',       'Imran',  'Siddiqui','imran.siddiqui@dps.edu.in', '+919811100141', 'SUBJECT_TEACHER',       '2026-03-20 05:25:00+00'),
('5eed0001-0001-4000-8000-000000000042', 'teacher_deepa',       'Deepa',  'Rao',     'deepa.rao@dps.edu.in',      '+919811100142', 'SUBJECT_TEACHER',       '2026-03-20 05:30:00+00'),
('5eed0001-0001-4000-8000-000000000043', 'teacher_arjun',       'Arjun',  'Mehta',   'arjun.mehta@dps.edu.in',    '+919811100143', 'SUBJECT_TEACHER',       '2026-03-20 05:35:00+00'),
('5eed0001-0001-4000-8000-000000000044', 'teacher_sana',        'Sana',   'Qureshi', 'sana.qureshi@dps.edu.in',   '+919811100144', 'SUBJECT_TEACHER',       '2026-03-20 05:40:00+00');

INSERT INTO users (user_id, tenant_id, branch_id, username, first_name, last_name, email, phone_number, user_type, is_active, is_mfa_enabled,
                   created_at, updated_at, created_by, updated_by, version)
SELECT user_id, 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', username, first_name, last_name, email, phone, user_type, true, false,
       created_at, created_at, 'seed:module06', NULL, 0
FROM seed_staff;

INSERT INTO user_credentials (user_id, password_hash, password_salt, failed_login_attempts, locked_until, must_change_password,
                              password_changed_at, created_at, updated_at, created_by, updated_by, version)
SELECT user_id, '$2a$10$8liDxOW98AN4N7eJQbqKd.LMq6EZ7rg6Q11jCO3sxeqrp39CXcXTq',                 -- Password@123
       'salt_' || substr(md5(user_id), 1, 8), 0, NULL, false, created_at + interval '10 minutes',
       created_at, created_at, 'seed:module06', NULL, 0
FROM seed_staff;

INSERT INTO user_roles (user_id, role_code, created_at, updated_at, created_by, updated_by, version)
SELECT user_id, user_type, created_at, created_at, 'seed:module06', NULL, 0 FROM seed_staff;

-- ---------------------------------------------------------------------------------------------------
-- 2. Subjects and the bell schedule (US-06000, US-06001)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO subjects (subject_id, tenant_id, subject_code, subject_name, subject_type, is_active, created_at, updated_at, created_by, updated_by, version)
SELECT s.id::uuid, 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', s.code, s.name, s.type, true,
       TIMESTAMPTZ '2026-03-21 05:00:00+00', TIMESTAMPTZ '2026-03-21 05:00:00+00', 'seed:module06', NULL, 0
FROM (VALUES
    ('5eed0006-0001-4000-8000-000000000001', 'ENG',  'English',          'THEORY'),
    ('5eed0006-0001-4000-8000-000000000002', 'HIN',  'Hindi',            'THEORY'),
    ('5eed0006-0001-4000-8000-000000000003', 'MATH', 'Mathematics',      'THEORY'),
    ('5eed0006-0001-4000-8000-000000000004', 'SCI',  'Science',          'PRACTICAL'),
    ('5eed0006-0001-4000-8000-000000000005', 'SST',  'Social Science',   'THEORY'),
    ('5eed0006-0001-4000-8000-000000000006', 'CS',   'Computer Science', 'ELECTIVE')
) AS s(id, code, name, type);

INSERT INTO bell_schedules (bell_schedule_id, tenant_id, branch_id, schedule_name, working_days, is_active, created_at, updated_at, created_by, updated_by, version)
VALUES ('5eed0006-0002-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'DPS North regular', '1,2,3,4,5,6', true,
        TIMESTAMPTZ '2026-03-21 05:30:00+00', TIMESTAMPTZ '2026-03-21 05:30:00+00', 'seed:module06', NULL, 0);

INSERT INTO bell_periods (bell_period_id, bell_schedule_id, sequence_no, slot_type, period_number, label, start_time, end_time,
                          created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0006-0003-4000-8000-0000000000' || lpad(p.seq::text, 2, '0'))::uuid, '5eed0006-0002-4000-8000-000000000001', p.seq, p.type, p.period,
       p.label, p.start::time, p.finish::time, TIMESTAMPTZ '2026-03-21 05:30:00+00', TIMESTAMPTZ '2026-03-21 05:30:00+00', 'seed:module06', NULL, 0
FROM (VALUES
    (1,  'ASSEMBLY', NULL, 'Assembly', '07:50', '08:10'),
    (2,  'PERIOD',   1,    'Period 1', '08:10', '08:50'),
    (3,  'PERIOD',   2,    'Period 2', '08:50', '09:30'),
    (4,  'PERIOD',   3,    'Period 3', '09:30', '10:10'),
    (5,  'RECESS',   NULL, 'Recess',   '10:10', '10:30'),
    (6,  'PERIOD',   4,    'Period 4', '10:30', '11:10'),
    (7,  'PERIOD',   5,    'Period 5', '11:10', '11:50'),
    (8,  'PERIOD',   6,    'Period 6', '11:50', '12:30'),
    (9,  'LUNCH',    NULL, 'Lunch',    '12:30', '13:00'),
    (10, 'PERIOD',   7,    'Period 7', '13:00', '13:40'),
    (11, 'PERIOD',   8,    'Period 8', '13:40', '14:20')
) AS p(seq, type, period, label, start, finish);

-- ---------------------------------------------------------------------------------------------------
-- 3. Workload caps and allocations for Class 6-A and 6-C (US-06001)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO faculty_workloads (workload_id, tenant_id, branch_id, teacher_id, teacher_name, weekly_max_periods, daily_max_periods,
                               qualified_subject_codes, created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0006-0004-4000-8000-00000000000' || w.n)::uuid, 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', w.teacher, w.name,
       w.weekly, w.daily, w.codes, TIMESTAMPTZ '2026-03-22 05:00:00+00', TIMESTAMPTZ '2026-03-22 05:00:00+00', 'seed:module06', NULL, 0
FROM (VALUES
    ('1', '5eed0001-0001-4000-8000-000000000034', 'Neha Kapoor',    30, 6, 'MATH,SCI'),
    ('2', '5eed0001-0001-4000-8000-000000000026', 'Priya Nair',     30, 6, 'ENG,HIN'),
    ('3', '5eed0001-0001-4000-8000-000000000040', 'Kavya Pillai',   30, 6, 'ENG'),
    ('4', '5eed0001-0001-4000-8000-000000000041', 'Imran Siddiqui', 30, 6, 'SST'),
    ('5', '5eed0001-0001-4000-8000-000000000042', 'Deepa Rao',      30, 6, 'SCI'),
    ('6', '5eed0001-0001-4000-8000-000000000043', 'Arjun Mehta',    20, 5, 'HIN,SST'),
    ('7', '5eed0001-0001-4000-8000-000000000044', 'Sana Qureshi',   30, 6, 'MATH,SCI')
) AS w(n, teacher, name, weekly, daily, codes);

INSERT INTO subject_allocations (allocation_id, tenant_id, grade_id, section_id, subject_id, teacher_id, periods_per_week,
                                 created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0006-0005-4000-8000-0000000000' || a.n)::uuid, 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0003-0002-4000-8000-000000000009',
       a.section::uuid, a.subject::uuid, a.teacher, a.periods,
       TIMESTAMPTZ '2026-03-22 06:00:00+00', TIMESTAMPTZ '2026-03-22 06:00:00+00', 'seed:module06', NULL, 0
FROM (VALUES
    -- Class 6-A
    ('11', '5eed0003-0003-4000-8000-000000000091', '5eed0006-0001-4000-8000-000000000001', '5eed0001-0001-4000-8000-000000000040', 7),
    ('12', '5eed0003-0003-4000-8000-000000000091', '5eed0006-0001-4000-8000-000000000002', '5eed0001-0001-4000-8000-000000000043', 5),
    ('13', '5eed0003-0003-4000-8000-000000000091', '5eed0006-0001-4000-8000-000000000003', '5eed0001-0001-4000-8000-000000000034', 7),
    ('14', '5eed0003-0003-4000-8000-000000000091', '5eed0006-0001-4000-8000-000000000004', '5eed0001-0001-4000-8000-000000000042', 6),
    ('15', '5eed0003-0003-4000-8000-000000000091', '5eed0006-0001-4000-8000-000000000005', '5eed0001-0001-4000-8000-000000000041', 6),
    -- Class 6-C
    ('21', '5eed0003-0003-4000-8000-000000000093', '5eed0006-0001-4000-8000-000000000001', '5eed0001-0001-4000-8000-000000000026', 7),
    ('22', '5eed0003-0003-4000-8000-000000000093', '5eed0006-0001-4000-8000-000000000002', '5eed0001-0001-4000-8000-000000000026', 5),
    ('23', '5eed0003-0003-4000-8000-000000000093', '5eed0006-0001-4000-8000-000000000003', '5eed0001-0001-4000-8000-000000000034', 7),
    ('24', '5eed0003-0003-4000-8000-000000000093', '5eed0006-0001-4000-8000-000000000004', '5eed0001-0001-4000-8000-000000000042', 6),
    ('25', '5eed0003-0003-4000-8000-000000000093', '5eed0006-0001-4000-8000-000000000005', '5eed0001-0001-4000-8000-000000000041', 6)
) AS a(n, section, subject, teacher, periods);

-- ---------------------------------------------------------------------------------------------------
-- 4. Class 6 Social Science syllabus and coverage (US-06004)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO syllabus_topics (topic_id, tenant_id, academic_year_id, grade_id, subject_id, sequence_no, chapter_title, planned_completion_date,
                             planned_periods, is_archived, created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0006-0006-4000-8000-00000000000' || t.seq)::uuid, 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0003-0001-4000-8000-000000000002',
       '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000005', t.seq, t.title, t.planned::date, t.periods, false,
       TIMESTAMPTZ '2026-03-25 05:00:00+00', TIMESTAMPTZ '2026-03-25 05:00:00+00', 'seed:module06', NULL, 0
FROM (VALUES
    (1, 'Locating Places on the Earth',      '2026-05-15', 10),
    (2, 'Oceans and Continents',              '2026-07-31', 10),
    (3, 'Timeline and Sources of History',    '2026-09-15', 8),
    (4, 'India, That Is Bharat',              '2026-10-31', 9),
    (5, 'The Beginnings of Indian Civilisation', '2026-12-15', 12),
    (6, 'Grassroots Democracy',               '2027-01-31', 10)
) AS t(seq, title, planned, periods);

INSERT INTO syllabus_coverage (coverage_id, topic_id, section_id, completed_on, completed_by, notes, created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0006-0007-4000-8000-0000000000' || c.n)::uuid, ('5eed0006-0006-4000-8000-00000000000' || c.topic)::uuid, c.section::uuid,
       c.done::date, 'imran.siddiqui@dps.edu.in', c.notes, c.done::timestamptz, c.done::timestamptz, 'seed:module06', NULL, 0
FROM (VALUES
    ('11', 1, '5eed0003-0003-4000-8000-000000000091', '2026-05-14', 'Globe and atlas activity'),
    ('12', 2, '5eed0003-0003-4000-8000-000000000091', '2026-07-29', NULL),
    ('13', 3, '5eed0003-0003-4000-8000-000000000091', '2026-09-12', 'Museum visit worksheet'),
    ('21', 1, '5eed0003-0003-4000-8000-000000000093', '2026-05-22', 'Delayed by the summer break')
) AS c(n, topic, section, done, notes);

-- ---------------------------------------------------------------------------------------------------
-- 5. Labs (US-06011)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO lab_rooms (room_id, tenant_id, branch_id, room_name, room_type, capacity, is_active, created_at, updated_at, created_by, updated_by, version)
VALUES ('5eed0006-0008-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'Computer Lab 1', 'COMPUTER_LAB', 40, true,
        TIMESTAMPTZ '2026-03-26 05:00:00+00', TIMESTAMPTZ '2026-03-26 05:00:00+00', 'seed:module06', NULL, 0),
       ('5eed0006-0008-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'Science Lab', 'SCIENCE_LAB', 24, true,
        TIMESTAMPTZ '2026-03-26 05:00:00+00', TIMESTAMPTZ '2026-03-26 05:00:00+00', 'seed:module06', NULL, 0);

COMMIT;
