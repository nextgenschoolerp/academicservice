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
--            allocations for Class 6-A and 6-C (ENG 7, HIN 5, MATH 7, SCI 6, SST 6 = 31 periods each); Farah Khan and Farhan
--            Qureshi as Computer Science teachers; allocations for Classes 1-A to 4-A and a Class 4-A Hindi homework (US-06010)
--            as set up through the app; homework with marked, late and missing submissions for Classes 1-A, 4-A, 6-A and 6-C
--   US-06002 no timetable rows: generate them with POST /api/v1/lms/timetables/generate (6-A, 6-C) as timetable_rahul
--   US-06004 Class 6 Social Science plan for 2026-2027 (6 chapters, April to January); 6-A has taught chapters 1-3,
--            6-C only chapter 1, so 6-C shows BEHIND on 19 Oct 2026; plus six-chapter plans for Classes 1-12 (about 50 class and
--            subject plans) with chapters 1-3 taught in the classes that have students (section 4b)
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
DELETE FROM homework_submissions    WHERE homework_id::text LIKE '5eed0006-%';
DELETE FROM homework_overdue_alerts WHERE homework_id::text LIKE '5eed0006-%';
DELETE FROM homework_assignments    WHERE homework_id::text LIKE '5eed0006-%';
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
SELECT user_id, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', username, first_name, last_name, email, phone, user_type, true, false,
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
SELECT s.id::uuid, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', s.code, s.name, s.type, true,
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
VALUES ('5eed0006-0002-4000-8000-000000000001', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'DPS North regular', '1,2,3,4,5,6', true,
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
SELECT ('5eed0006-0004-4000-8000-00000000000' || w.n)::uuid, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', w.teacher, w.name,
       w.weekly, w.daily, w.codes, TIMESTAMPTZ '2026-03-22 05:00:00+00', TIMESTAMPTZ '2026-03-22 05:00:00+00', 'seed:module06', NULL, 0
FROM (VALUES
    ('1', '5eed0001-0001-4000-8000-000000000034', 'Neha Kapoor',    30, 6, 'MATH,SCI'),
    ('2', '5eed0001-0001-4000-8000-000000000026', 'Priya Nair',     30, 6, 'ENG,HIN'),
    ('3', '5eed0001-0001-4000-8000-000000000040', 'Kavya Pillai',   30, 6, 'ENG'),
    ('4', '5eed0001-0001-4000-8000-000000000041', 'Imran Siddiqui', 30, 6, 'SST'),
    ('5', '5eed0001-0001-4000-8000-000000000042', 'Deepa Rao',      30, 6, 'SCI'),
    ('6', '5eed0001-0001-4000-8000-000000000043', 'Arjun Mehta',    20, 5, 'HIN,SST'),
    ('7', '5eed0001-0001-4000-8000-000000000044', 'Sana Qureshi',   30, 6, 'MATH,SCI'),
    ('8', '5eed0001-0001-4000-8000-000000000039', 'Farah Khan',     30, 6, 'CS'),
    ('9', '5eed0001-0001-4000-8000-000000000016', 'Farhan Qureshi', 30, 6, 'CS')
) AS w(n, teacher, name, weekly, daily, codes);

INSERT INTO subject_allocations (allocation_id, tenant_id, grade_id, section_id, subject_id, teacher_id, periods_per_week,
                                 created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0006-0005-4000-8000-0000000000' || a.n)::uuid, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0003-0002-4000-8000-000000000009',
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
SELECT ('5eed0006-0006-4000-8000-00000000000' || t.seq)::uuid, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0003-0001-4000-8000-000000000002',
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
-- 3b. Allocations for Classes 1-A to 4-A, as set up through the app (Computer Science with Farah Khan in Class 1-A);
--     with the Farah Khan and Farhan Qureshi workloads above they make Computer Science a two-teacher subject
-- ---------------------------------------------------------------------------------------------------
INSERT INTO subject_allocations (allocation_id, tenant_id, grade_id, section_id, subject_id, teacher_id, periods_per_week,
                                 created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0006-0005-4000-8000-0000000000' || a.n)::uuid, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', a.grade::uuid, a.section::uuid, a.subject::uuid, a.teacher, a.periods,
       TIMESTAMPTZ '2026-03-22 06:30:00+00', TIMESTAMPTZ '2026-03-22 06:30:00+00', 'seed:module06', NULL, 0
FROM (VALUES
    ('31', '5eed0003-0002-4000-8000-000000000004', '5eed0003-0003-4000-8000-000000000041', '5eed0006-0001-4000-8000-000000000006', '5eed0001-0001-4000-8000-000000000039', 5),
    ('32', '5eed0003-0002-4000-8000-000000000004', '5eed0003-0003-4000-8000-000000000041', '5eed0006-0001-4000-8000-000000000001', '5eed0001-0001-4000-8000-000000000026', 5),
    ('33', '5eed0003-0002-4000-8000-000000000004', '5eed0003-0003-4000-8000-000000000041', '5eed0006-0001-4000-8000-000000000003', '5eed0001-0001-4000-8000-000000000034', 5),
    ('34', '5eed0003-0002-4000-8000-000000000004', '5eed0003-0003-4000-8000-000000000041', '5eed0006-0001-4000-8000-000000000004', '5eed0001-0001-4000-8000-000000000042', 5),
    ('35', '5eed0003-0002-4000-8000-000000000004', '5eed0003-0003-4000-8000-000000000041', '5eed0006-0001-4000-8000-000000000005', '5eed0001-0001-4000-8000-000000000043', 5),
    ('36', '5eed0003-0002-4000-8000-000000000005', '5eed0003-0003-4000-8000-000000000051', '5eed0006-0001-4000-8000-000000000001', '5eed0001-0001-4000-8000-000000000026', 5),
    ('37', '5eed0003-0002-4000-8000-000000000005', '5eed0003-0003-4000-8000-000000000051', '5eed0006-0001-4000-8000-000000000002', '5eed0001-0001-4000-8000-000000000043', 5),
    ('38', '5eed0003-0002-4000-8000-000000000005', '5eed0003-0003-4000-8000-000000000051', '5eed0006-0001-4000-8000-000000000004', '5eed0001-0001-4000-8000-000000000034', 5),
    ('39', '5eed0003-0002-4000-8000-000000000006', '5eed0003-0003-4000-8000-000000000061', '5eed0006-0001-4000-8000-000000000001', '5eed0001-0001-4000-8000-000000000026', 5),
    ('40', '5eed0003-0002-4000-8000-000000000006', '5eed0003-0003-4000-8000-000000000061', '5eed0006-0001-4000-8000-000000000002', '5eed0001-0001-4000-8000-000000000043', 5),
    ('41', '5eed0003-0002-4000-8000-000000000006', '5eed0003-0003-4000-8000-000000000061', '5eed0006-0001-4000-8000-000000000004', '5eed0001-0001-4000-8000-000000000044', 5),
    ('42', '5eed0003-0002-4000-8000-000000000006', '5eed0003-0003-4000-8000-000000000061', '5eed0006-0001-4000-8000-000000000005', '5eed0001-0001-4000-8000-000000000041', 5),
    ('43', '5eed0003-0002-4000-8000-000000000007', '5eed0003-0003-4000-8000-000000000071', '5eed0006-0001-4000-8000-000000000001', '5eed0001-0001-4000-8000-000000000040', 5),
    ('44', '5eed0003-0002-4000-8000-000000000007', '5eed0003-0003-4000-8000-000000000071', '5eed0006-0001-4000-8000-000000000002', '5eed0001-0001-4000-8000-000000000026', 3),
    ('45', '5eed0003-0002-4000-8000-000000000007', '5eed0003-0003-4000-8000-000000000071', '5eed0006-0001-4000-8000-000000000003', '5eed0001-0001-4000-8000-000000000034', 5),
    ('46', '5eed0003-0002-4000-8000-000000000007', '5eed0003-0003-4000-8000-000000000071', '5eed0006-0001-4000-8000-000000000004', '5eed0001-0001-4000-8000-000000000042', 5)
) AS a(n, grade, section, subject, teacher, periods);

-- ---------------------------------------------------------------------------------------------------
-- 3c. Class 4-A Hindi homework (US-06010), set by Priya Nair; due 27 Oct 2026
-- ---------------------------------------------------------------------------------------------------
INSERT INTO homework_assignments (homework_id, tenant_id, grade_id, section_id, subject_id, teacher_id, title, instructions, assigned_on, due_at,
                                  max_marks, asset_id, created_at, updated_at, created_by, updated_by, version)
VALUES ('5eed0006-0009-4000-8000-000000000001', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0003-0002-4000-8000-000000000007', '5eed0003-0003-4000-8000-000000000071', '5eed0006-0001-4000-8000-000000000002',
        '5eed0001-0001-4000-8000-000000000026', 'Hindi Grammar', 'As per the instruction', DATE '2026-10-25', TIMESTAMPTZ '2026-10-27 09:00:00+05:30',
        10, NULL, TIMESTAMPTZ '2026-10-25 01:13:36+00', TIMESTAMPTZ '2026-10-25 01:13:36+00', 'seed:module06', NULL, 0);

-- ---------------------------------------------------------------------------------------------------
-- 3d. Homework and submissions (US-06010) for Classes 1-A, 4-A, 6-A and 6-C, the classes with allocated teachers and
--     students. Past due and marked, handed in late, waiting to be marked, missing, and not yet due all appear.
--     Homework ids 5eed0006-0009-…0002 to …0010 (…0001 is the Class 4-A Hindi homework above), submission ids 5eed0006-000a-…
-- ---------------------------------------------------------------------------------------------------
INSERT INTO homework_assignments (homework_id, tenant_id, grade_id, section_id, subject_id, teacher_id, title, instructions, assigned_on, due_at,
                                  max_marks, asset_id, created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0006-0009-4000-8000-0000000000' || h.n)::uuid, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', h.grade::uuid, h.section::uuid, h.subject::uuid, h.teacher, h.title, h.instructions,
       h.assigned::date, (h.due || '+05:30')::timestamptz, h.max, NULL,
       (h.assigned || ' 09:00+05:30')::timestamptz, (h.assigned || ' 09:00+05:30')::timestamptz, 'seed:module06', NULL, 0
FROM (VALUES
    ('02', '5eed0003-0002-4000-8000-000000000004', '5eed0003-0003-4000-8000-000000000041', '5eed0006-0001-4000-8000-000000000003', '5eed0001-0001-4000-8000-000000000034', 'Addition up to 20 worksheet', 'Complete the worksheet on page 14 and bring it to class', '2026-10-19', '2026-10-23 09:00', 10),
    ('03', '5eed0003-0002-4000-8000-000000000004', '5eed0003-0003-4000-8000-000000000041', '5eed0006-0001-4000-8000-000000000001', '5eed0001-0001-4000-8000-000000000026', 'Draw and name five animals', 'Draw five animals and write their names under each picture', '2026-10-22', '2026-10-30 09:00', 10),
    ('04', '5eed0003-0002-4000-8000-000000000007', '5eed0003-0003-4000-8000-000000000071', '5eed0006-0001-4000-8000-000000000003', '5eed0001-0001-4000-8000-000000000034', 'Exercise 2: Building with Bricks', 'Solve questions 1 to 8 in your notebook', '2026-10-20', '2026-10-24 09:00', 20),
    ('05', '5eed0003-0002-4000-8000-000000000007', '5eed0003-0003-4000-8000-000000000071', '5eed0006-0001-4000-8000-000000000001', '5eed0001-0001-4000-8000-000000000040', 'A paragraph on Nasruddin', 'Write five lines about Nasruddin and his aim', '2026-10-23', '2026-11-02 09:00', 10),
    ('06', '5eed0003-0002-4000-8000-000000000009', '5eed0003-0003-4000-8000-000000000091', '5eed0006-0001-4000-8000-000000000003', '5eed0001-0001-4000-8000-000000000034', 'Exercise 1.2: Knowing Our Numbers', 'Questions 1 to 10, and show your working', '2026-10-15', '2026-10-21 09:00', 20),
    ('07', '5eed0003-0002-4000-8000-000000000009', '5eed0003-0003-4000-8000-000000000091', '5eed0006-0001-4000-8000-000000000004', '5eed0001-0001-4000-8000-000000000042', 'Food and its components chart', 'Make a chart of the food groups with pictures', '2026-10-22', '2026-10-29 09:00', 10),
    ('08', '5eed0003-0002-4000-8000-000000000009', '5eed0003-0003-4000-8000-000000000091', '5eed0006-0001-4000-8000-000000000001', '5eed0001-0001-4000-8000-000000000040', 'Summary of The Friendly Mongoose', 'Write a summary in about 80 words', '2026-10-14', '2026-10-20 09:00', 10),
    ('09', '5eed0003-0002-4000-8000-000000000009', '5eed0003-0003-4000-8000-000000000093', '5eed0006-0001-4000-8000-000000000002', '5eed0001-0001-4000-8000-000000000026', 'Vah Chidiya Jo: prashn uttar', 'Answer the questions at the end of the poem', '2026-10-19', '2026-10-26 09:00', 10),
    ('10', '5eed0003-0002-4000-8000-000000000009', '5eed0003-0003-4000-8000-000000000093', '5eed0006-0001-4000-8000-000000000005', '5eed0001-0001-4000-8000-000000000041', 'Map work: continents and oceans', 'Mark the seven continents and five oceans on the outline map', '2026-10-23', '2026-10-30 09:00', 10)
) AS h(n, grade, section, subject, teacher, title, instructions, assigned, due, max);

INSERT INTO homework_submissions (submission_id, homework_id, student_id, submitted_at, submitted_by, is_late, status, marks_awarded, feedback,
                                  evaluated_at, evaluated_by, submission_text, attachment_url, annotations_json,
                                  created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0006-000a-4000-8000-0000000000' || s.n)::uuid, ('5eed0006-0009-4000-8000-0000000000' || s.hw)::uuid, s.student::uuid,
       (s.at || '+05:30')::timestamptz, 'seed:module06', s.late, s.status, s.marks, s.feedback,
       CASE WHEN s.status = 'EVALUATED' THEN (s.at || '+05:30')::timestamptz + INTERVAL '1 day' END, NULLIF(s.evaluator, ''),
       'Done as instructed', NULL, NULL,
       (s.at || '+05:30')::timestamptz, (s.at || '+05:30')::timestamptz, 'seed:module06', NULL, 0
FROM (VALUES
    ('01', '02', '5eed0003-0005-4000-8000-000000000013', '2026-10-22 18:30', false, 'EVALUATED', 9, 'Neat work', 'neha.kapoor@dps.edu.in'),
    ('02', '02', '5eed0003-0005-4000-8000-000000000024', '2026-10-23 08:15', false, 'EVALUATED', 7, 'Check the last three sums', 'neha.kapoor@dps.edu.in'),
    ('03', '02', '5eed0003-0005-4000-8000-000000000036', '2026-10-23 11:40', true, 'SUBMITTED', NULL, NULL, ''),
    ('04', '03', '5eed0003-0005-4000-8000-000000000024', '2026-10-26 17:05', false, 'SUBMITTED', NULL, NULL, ''),
    ('05', '04', '5eed0003-0005-4000-8000-000000000014', '2026-10-23 19:10', false, 'EVALUATED', 17, 'Very good', 'neha.kapoor@dps.edu.in'),
    ('06', '04', '5eed0003-0005-4000-8000-000000000015', '2026-10-23 20:05', false, 'EVALUATED', 15, NULL, 'neha.kapoor@dps.edu.in'),
    ('07', '04', '5eed0003-0005-4000-8000-000000000027', '2026-10-24 07:50', false, 'EVALUATED', 12, 'Show the working in question 6', 'neha.kapoor@dps.edu.in'),
    ('08', '04', '5eed0003-0005-4000-8000-000000000039', '2026-10-24 10:05', true, 'SUBMITTED', NULL, NULL, ''),
    ('09', '06', '5eed0003-0005-4000-8000-000000000001', '2026-10-20 17:45', false, 'EVALUATED', 18, 'Excellent', 'neha.kapoor@dps.edu.in'),
    ('10', '06', '5eed0003-0005-4000-8000-000000000002', '2026-10-21 08:30', false, 'EVALUATED', 16, NULL, 'neha.kapoor@dps.edu.in'),
    ('11', '06', '5eed0003-0005-4000-8000-000000000003', '2026-10-21 08:55', false, 'EVALUATED', 11, 'Revise place value', 'neha.kapoor@dps.edu.in'),
    ('12', '07', '5eed0003-0005-4000-8000-000000000001', '2026-10-26 19:00', false, 'SUBMITTED', NULL, NULL, ''),
    ('13', '08', '5eed0003-0005-4000-8000-000000000001', '2026-10-19 18:20', false, 'EVALUATED', 8, 'Good summary', 'kavya.pillai@dps.edu.in'),
    ('14', '08', '5eed0003-0005-4000-8000-000000000002', '2026-10-20 14:10', true, 'SUBMITTED', NULL, NULL, ''),
    ('15', '09', '5eed0003-0005-4000-8000-000000000017', '2026-10-25 16:40', false, 'EVALUATED', 9, NULL, 'priya.nair@dps.edu.in'),
    ('16', '09', '5eed0003-0005-4000-8000-000000000029', '2026-10-26 08:20', false, 'EVALUATED', 6, 'Answer in full sentences', 'priya.nair@dps.edu.in')
) AS s(n, hw, student, at, late, status, marks, feedback, evaluator);

-- ---------------------------------------------------------------------------------------------------
-- 4b. Syllabus plans for Classes 1-12 (US-06004): six chapters a subject, May to January, for the core subjects of each
--     class (English, Hindi, Mathematics and Science to Class 10, Social Science from Class 7, Computer Science in
--     Classes 11-12). Chapters 1-3 are taught in every class with students, except Classes 3-C, 6-B, 9-C and 12-C,
--     which have taught chapters 1-2 and show BEHIND on 27 Oct 2026. Topic ids 5eed0006-0006-4000-8001-…, coverage
--     ids 5eed0006-0007-4000-8001-… (the Class 6 Social Science plan above keeps its own ids).
-- ---------------------------------------------------------------------------------------------------
INSERT INTO syllabus_topics (topic_id, tenant_id, academic_year_id, grade_id, subject_id, sequence_no, chapter_title, planned_completion_date,
                             planned_periods, is_archived, created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0006-0006-4000-8001-' || lpad(t.id::text, 12, '0'))::uuid, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0003-0001-4000-8000-000000000002',
       t.grade::uuid, t.subject::uuid, t.seq, t.title, t.planned::date, t.periods, false,
       TIMESTAMPTZ '2026-03-25 06:00:00+00', TIMESTAMPTZ '2026-03-25 06:00:00+00', 'seed:module06', NULL, 0
FROM (VALUES
    (1, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000001', 1, 'Hello Friends', '2026-05-15', 8),
    (2, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000001', 2, 'My Family', '2026-07-31', 10),
    (3, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000001', 3, 'Colours Around Us', '2026-09-15', 9),
    (4, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000001', 4, 'Animals We Love', '2026-10-31', 10),
    (5, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000001', 5, 'The Little Bird', '2026-12-15', 12),
    (6, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000001', 6, 'Fruits and Vegetables', '2027-01-31', 10),
    (7, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000002', 1, 'Varnamala: Swar', '2026-05-15', 8),
    (8, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000002', 2, 'Varnamala: Vyanjan', '2026-07-31', 10),
    (9, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000002', 3, 'Shabd Bina Matra Ke', '2026-09-15', 9),
    (10, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000002', 4, 'Chhoti Matra Wale Shabd', '2026-10-31', 10),
    (11, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000002', 5, 'Meri Kitab', '2026-12-15', 12),
    (12, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000002', 6, 'Phal Aur Sabziyan', '2027-01-31', 10),
    (13, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000003', 1, 'Numbers 1 to 20', '2026-05-15', 8),
    (14, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000003', 2, 'Counting Objects', '2026-07-31', 10),
    (15, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000003', 3, 'Shapes Around Us', '2026-09-15', 9),
    (16, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000003', 4, 'Addition up to 20', '2026-10-31', 10),
    (17, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000003', 5, 'Subtraction up to 20', '2026-12-15', 12),
    (18, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000003', 6, 'Time and Days of the Week', '2027-01-31', 10),
    (19, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000004', 1, 'My Body', '2026-05-15', 8),
    (20, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000004', 2, 'Plants Around Us', '2026-07-31', 10),
    (21, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000004', 3, 'Animals and Their Homes', '2026-09-15', 9),
    (22, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000004', 4, 'Food We Eat', '2026-10-31', 10),
    (23, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000004', 5, 'Air and Water', '2026-12-15', 12),
    (24, '5eed0003-0002-4000-8000-000000000004', '5eed0006-0001-4000-8000-000000000004', 6, 'Our Senses', '2027-01-31', 10),
    (25, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000001', 1, 'First Day at School', '2026-05-15', 8),
    (26, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000001', 2, 'Haldi''s Adventure', '2026-07-31', 10),
    (27, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000001', 3, 'The Tiger and the Mosquito', '2026-09-15', 9),
    (28, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000001', 4, 'The Cat and the Rat', '2026-10-31', 10),
    (29, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000001', 5, 'Our Pets', '2026-12-15', 12),
    (30, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000001', 6, 'The Wind and the Sun', '2027-01-31', 10),
    (31, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000002', 1, 'Kaun Si Kahani', '2026-05-15', 8),
    (32, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000002', 2, 'Chidiya Ki Udaan', '2026-07-31', 10),
    (33, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000002', 3, 'Gillu Ki Kahani', '2026-09-15', 9),
    (34, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000002', 4, 'Aam Ki Kahani', '2026-10-31', 10),
    (35, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000002', 5, 'Meri Maa', '2026-12-15', 12),
    (36, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000002', 6, 'Mausam', '2027-01-31', 10),
    (37, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000003', 1, 'Counting in Groups', '2026-05-15', 8),
    (38, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000003', 2, 'Numbers up to 100', '2026-07-31', 10),
    (39, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000003', 3, 'Addition and Subtraction', '2026-09-15', 9),
    (40, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000003', 4, 'Multiplication Basics', '2026-10-31', 10),
    (41, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000003', 5, 'Measurement', '2026-12-15', 12),
    (42, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000003', 6, 'Money and Time', '2027-01-31', 10),
    (43, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000004', 1, 'Plants and Animals', '2026-05-15', 8),
    (44, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000004', 2, 'Our Homes', '2026-07-31', 10),
    (45, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000004', 3, 'Water and Weather', '2026-09-15', 9),
    (46, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000004', 4, 'Good Habits', '2026-10-31', 10),
    (47, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000004', 5, 'Transport', '2026-12-15', 12),
    (48, '5eed0003-0002-4000-8000-000000000005', '5eed0006-0001-4000-8000-000000000004', 6, 'Safety Rules', '2027-01-31', 10),
    (49, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000001', 1, 'Good Morning', '2026-05-15', 8),
    (50, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000001', 2, 'The Magic Garden', '2026-07-31', 10),
    (51, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000001', 3, 'Bird Talk', '2026-09-15', 9),
    (52, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000001', 4, 'Little by Little', '2026-10-31', 10),
    (53, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000001', 5, 'The Enormous Turnip', '2026-12-15', 12),
    (54, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000001', 6, 'Sea Song', '2027-01-31', 10),
    (55, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000002', 1, 'Kakku', '2026-05-15', 8),
    (56, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000002', 2, 'Shekhibaaz Makkhi', '2026-07-31', 10),
    (57, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000002', 3, 'Chand Wali Amma', '2026-09-15', 9),
    (58, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000002', 4, 'Man Karta Hai', '2026-10-31', 10),
    (59, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000002', 5, 'Bahadur Bitiya', '2026-12-15', 12),
    (60, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000002', 6, 'Hum Panchhi Unmukt Gagan Ke', '2027-01-31', 10),
    (61, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000003', 1, 'Where to Look From', '2026-05-15', 8),
    (62, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000003', 2, 'Fun with Numbers', '2026-07-31', 10),
    (63, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000003', 3, 'Give and Take', '2026-09-15', 9),
    (64, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000003', 4, 'Long and Short', '2026-10-31', 10),
    (65, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000003', 5, 'Shapes and Designs', '2026-12-15', 12),
    (66, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000003', 6, 'Fun with Give and Take', '2027-01-31', 10),
    (67, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000004', 1, 'Poonam''s Day Out', '2026-05-15', 8),
    (68, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000004', 2, 'The Plant Fairy', '2026-07-31', 10),
    (69, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000004', 3, 'Water O Water', '2026-09-15', 9),
    (70, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000004', 4, 'Our First School', '2026-10-31', 10),
    (71, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000004', 5, 'Chhotu''s House', '2026-12-15', 12),
    (72, '5eed0003-0002-4000-8000-000000000006', '5eed0006-0001-4000-8000-000000000004', 6, 'Foods We Eat', '2027-01-31', 10),
    (73, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000001', 1, 'Wake Up', '2026-05-15', 8),
    (74, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000001', 2, 'Neha''s Alarm Clock', '2026-07-31', 10),
    (75, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000001', 3, 'Noses', '2026-09-15', 9),
    (76, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000001', 4, 'The Little Fir Tree', '2026-10-31', 10),
    (77, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000001', 5, 'Run', '2026-12-15', 12),
    (78, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000001', 6, 'Nasruddin''s Aim', '2027-01-31', 10),
    (79, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000002', 1, 'Man Ke Bhole Bhale Badal', '2026-05-15', 8),
    (80, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000002', 2, 'Jaisa Sawal Waisa Jawab', '2026-07-31', 10),
    (81, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000002', 3, 'Kirmich Ki Gend', '2026-09-15', 9),
    (82, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000002', 4, 'Papa Jab Bachche The', '2026-10-31', 10),
    (83, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000002', 5, 'Dost Ki Poshak', '2026-12-15', 12),
    (84, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000002', 6, 'Naav Banao', '2027-01-31', 10),
    (85, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000003', 1, 'Building with Bricks', '2026-05-15', 8),
    (86, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000003', 2, 'Long and Short', '2026-07-31', 10),
    (87, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000003', 3, 'A Trip to Bhopal', '2026-09-15', 9),
    (88, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000003', 4, 'Tick-Tick-Tick', '2026-10-31', 10),
    (89, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000003', 5, 'The Way the World Looks', '2026-12-15', 12),
    (90, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000003', 6, 'The Junk Seller', '2027-01-31', 10),
    (91, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000004', 1, 'Going to School', '2026-05-15', 8),
    (92, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000004', 2, 'Ear to Ear', '2026-07-31', 10),
    (93, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000004', 3, 'A Day with Nandu', '2026-09-15', 9),
    (94, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000004', 4, 'The Story of Amrita', '2026-10-31', 10),
    (95, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000004', 5, 'Anita and the Honeybees', '2026-12-15', 12),
    (96, '5eed0003-0002-4000-8000-000000000007', '5eed0006-0001-4000-8000-000000000004', 6, 'Omana''s Journey', '2027-01-31', 10),
    (97, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000001', 1, 'Ice-cream Man', '2026-05-15', 8),
    (98, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000001', 2, 'Wonderful Waste', '2026-07-31', 10),
    (99, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000001', 3, 'Teamwork', '2026-09-15', 9),
    (100, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000001', 4, 'Flying Together', '2026-10-31', 10),
    (101, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000001', 5, 'My Shadow', '2026-12-15', 12),
    (102, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000001', 6, 'Robinson Crusoe Discovers a Footprint', '2027-01-31', 10),
    (103, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000002', 1, 'Rakh Ki Rassi', '2026-05-15', 8),
    (104, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000002', 2, 'Faslon Ke Tyohar', '2026-07-31', 10),
    (105, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000002', 3, 'Khilonewala', '2026-09-15', 9),
    (106, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000002', 4, 'Nanha Fankar', '2026-10-31', 10),
    (107, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000002', 5, 'Jahan Chah Wahan Raah', '2026-12-15', 12),
    (108, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000002', 6, 'Chitthi Ka Safar', '2027-01-31', 10),
    (109, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000003', 1, 'The Fish Tale', '2026-05-15', 8),
    (110, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000003', 2, 'Shapes and Angles', '2026-07-31', 10),
    (111, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000003', 3, 'How Many Squares', '2026-09-15', 9),
    (112, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000003', 4, 'Parts and Wholes', '2026-10-31', 10),
    (113, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000003', 5, 'Does It Look the Same', '2026-12-15', 12),
    (114, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000003', 6, 'Be My Multiple', '2027-01-31', 10),
    (115, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000004', 1, 'Super Senses', '2026-05-15', 8),
    (116, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000004', 2, 'A Snake Charmer''s Story', '2026-07-31', 10),
    (117, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000004', 3, 'From Tasting to Digesting', '2026-09-15', 9),
    (118, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000004', 4, 'Mangoes Round the Year', '2026-10-31', 10),
    (119, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000004', 5, 'Seeds and Seeds', '2026-12-15', 12),
    (120, '5eed0003-0002-4000-8000-000000000008', '5eed0006-0001-4000-8000-000000000004', 6, 'Every Drop Counts', '2027-01-31', 10),
    (121, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000001', 1, 'A Tale of Two Birds', '2026-05-15', 8),
    (122, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000001', 2, 'The Friendly Mongoose', '2026-07-31', 10),
    (123, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000001', 3, 'The Shepherd''s Treasure', '2026-09-15', 9),
    (124, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000001', 4, 'The Old-Clock Shop', '2026-10-31', 10),
    (125, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000001', 5, 'Tansen', '2026-12-15', 12),
    (126, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000001', 6, 'The Monkey and the Crocodile', '2027-01-31', 10),
    (127, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000002', 1, 'Vah Chidiya Jo', '2026-05-15', 8),
    (128, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000002', 2, 'Bachpan', '2026-07-31', 10),
    (129, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000002', 3, 'Nadan Dost', '2026-09-15', 9),
    (130, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000002', 4, 'Chand Se Thodi Si Gappe', '2026-10-31', 10),
    (131, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000002', 5, 'Akshron Ka Mahatva', '2026-12-15', 12),
    (132, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000002', 6, 'Paar Nazar Ke', '2027-01-31', 10),
    (133, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000003', 1, 'Knowing Our Numbers', '2026-05-15', 8),
    (134, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000003', 2, 'Whole Numbers', '2026-07-31', 10),
    (135, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000003', 3, 'Playing with Numbers', '2026-09-15', 9),
    (136, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000003', 4, 'Basic Geometrical Ideas', '2026-10-31', 10),
    (137, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000003', 5, 'Understanding Elementary Shapes', '2026-12-15', 12),
    (138, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000003', 6, 'Integers', '2027-01-31', 10),
    (139, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000004', 1, 'Food: Where Does It Come From', '2026-05-15', 8),
    (140, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000004', 2, 'Components of Food', '2026-07-31', 10),
    (141, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000004', 3, 'Fibre to Fabric', '2026-09-15', 9),
    (142, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000004', 4, 'Sorting Materials into Groups', '2026-10-31', 10),
    (143, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000004', 5, 'Separation of Substances', '2026-12-15', 12),
    (144, '5eed0003-0002-4000-8000-000000000009', '5eed0006-0001-4000-8000-000000000004', 6, 'Changes Around Us', '2027-01-31', 10),
    (145, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000001', 1, 'Three Questions', '2026-05-15', 8),
    (146, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000001', 2, 'A Gift of Chappals', '2026-07-31', 10),
    (147, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000001', 3, 'Gopal and the Hilsa-Fish', '2026-09-15', 9),
    (148, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000001', 4, 'The Ashes That Made Trees Bloom', '2026-10-31', 10),
    (149, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000001', 5, 'Quality', '2026-12-15', 12),
    (150, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000001', 6, 'Expert Detectives', '2027-01-31', 10),
    (151, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000002', 1, 'Hum Panchhi Unmukt Gagan Ke', '2026-05-15', 8),
    (152, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000002', 2, 'Dadi Maa', '2026-07-31', 10),
    (153, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000002', 3, 'Himalaya Ki Betiyan', '2026-09-15', 9),
    (154, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000002', 4, 'Kathputli', '2026-10-31', 10),
    (155, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000002', 5, 'Mithaiwala', '2026-12-15', 12),
    (156, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000002', 6, 'Raktchap Aur Namak', '2027-01-31', 10),
    (157, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000003', 1, 'Integers', '2026-05-15', 8),
    (158, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000003', 2, 'Fractions and Decimals', '2026-07-31', 10),
    (159, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000003', 3, 'Data Handling', '2026-09-15', 9),
    (160, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000003', 4, 'Simple Equations', '2026-10-31', 10),
    (161, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000003', 5, 'Lines and Angles', '2026-12-15', 12),
    (162, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000003', 6, 'The Triangle and Its Properties', '2027-01-31', 10),
    (163, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000004', 1, 'Nutrition in Plants', '2026-05-15', 8),
    (164, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000004', 2, 'Nutrition in Animals', '2026-07-31', 10),
    (165, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000004', 3, 'Fibre to Fabric', '2026-09-15', 9),
    (166, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000004', 4, 'Heat', '2026-10-31', 10),
    (167, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000004', 5, 'Acids, Bases and Salts', '2026-12-15', 12),
    (168, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000004', 6, 'Physical and Chemical Changes', '2027-01-31', 10),
    (169, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000005', 1, 'Tracing Changes Through a Thousand Years', '2026-05-15', 8),
    (170, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000005', 2, 'New Kings and Kingdoms', '2026-07-31', 10),
    (171, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000005', 3, 'The Delhi Sultans', '2026-09-15', 9),
    (172, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000005', 4, 'The Mughal Empire', '2026-10-31', 10),
    (173, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000005', 5, 'Environment', '2026-12-15', 12),
    (174, '5eed0003-0002-4000-8000-000000000010', '5eed0006-0001-4000-8000-000000000005', 6, 'Inside Our Earth', '2027-01-31', 10),
    (175, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000001', 1, 'The Best Christmas Present in the World', '2026-05-15', 8),
    (176, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000001', 2, 'The Tsunami', '2026-07-31', 10),
    (177, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000001', 3, 'Glimpses of the Past', '2026-09-15', 9),
    (178, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000001', 4, 'Bepin Choudhury''s Lapse of Memory', '2026-10-31', 10),
    (179, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000001', 5, 'The Summit Within', '2026-12-15', 12),
    (180, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000001', 6, 'This Is Jody''s Fawn', '2027-01-31', 10),
    (181, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000002', 1, 'Dhwani', '2026-05-15', 8),
    (182, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000002', 2, 'Lakh Ki Chudiyan', '2026-07-31', 10),
    (183, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000002', 3, 'Bus Ki Yatra', '2026-09-15', 9),
    (184, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000002', 4, 'Deewano Ki Hasti', '2026-10-31', 10),
    (185, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000002', 5, 'Chitthiyon Ki Anoothi Duniya', '2026-12-15', 12),
    (186, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000002', 6, 'Bhagwan Ke Dakiye', '2027-01-31', 10),
    (187, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000003', 1, 'Rational Numbers', '2026-05-15', 8),
    (188, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000003', 2, 'Linear Equations in One Variable', '2026-07-31', 10),
    (189, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000003', 3, 'Understanding Quadrilaterals', '2026-09-15', 9),
    (190, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000003', 4, 'Practical Geometry', '2026-10-31', 10),
    (191, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000003', 5, 'Data Handling', '2026-12-15', 12),
    (192, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000003', 6, 'Squares and Square Roots', '2027-01-31', 10),
    (193, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000004', 1, 'Crop Production and Management', '2026-05-15', 8),
    (194, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000004', 2, 'Microorganisms', '2026-07-31', 10),
    (195, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000004', 3, 'Synthetic Fibres and Plastics', '2026-09-15', 9),
    (196, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000004', 4, 'Metals and Non-metals', '2026-10-31', 10),
    (197, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000004', 5, 'Coal and Petroleum', '2026-12-15', 12),
    (198, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000004', 6, 'Combustion and Flame', '2027-01-31', 10),
    (199, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000005', 1, 'How, When and Where', '2026-05-15', 8),
    (200, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000005', 2, 'From Trade to Territory', '2026-07-31', 10),
    (201, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000005', 3, 'Ruling the Countryside', '2026-09-15', 9),
    (202, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000005', 4, 'Tribals, Dikus and the Vision of a Golden Age', '2026-10-31', 10),
    (203, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000005', 5, 'When People Rebel', '2026-12-15', 12),
    (204, '5eed0003-0002-4000-8000-000000000011', '5eed0006-0001-4000-8000-000000000005', 6, 'Resources', '2027-01-31', 10),
    (205, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000001', 1, 'The Fun They Had', '2026-05-15', 8),
    (206, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000001', 2, 'The Sound of Music', '2026-07-31', 10),
    (207, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000001', 3, 'The Little Girl', '2026-09-15', 9),
    (208, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000001', 4, 'A Truly Beautiful Mind', '2026-10-31', 10),
    (209, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000001', 5, 'The Snake and the Mirror', '2026-12-15', 12),
    (210, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000001', 6, 'My Childhood', '2027-01-31', 10),
    (211, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000002', 1, 'Do Bailon Ki Katha', '2026-05-15', 8),
    (212, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000002', 2, 'Lhasa Ki Or', '2026-07-31', 10),
    (213, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000002', 3, 'Upbhoktavad Ki Sanskriti', '2026-09-15', 9),
    (214, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000002', 4, 'Sawaiye Aur Kavitt', '2026-10-31', 10),
    (215, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000002', 5, 'Kaidi Aur Kokila', '2026-12-15', 12),
    (216, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000002', 6, 'Gillu', '2027-01-31', 10),
    (217, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000003', 1, 'Number Systems', '2026-05-15', 8),
    (218, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000003', 2, 'Polynomials', '2026-07-31', 10),
    (219, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000003', 3, 'Coordinate Geometry', '2026-09-15', 9),
    (220, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000003', 4, 'Linear Equations in Two Variables', '2026-10-31', 10),
    (221, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000003', 5, 'Introduction to Euclid''s Geometry', '2026-12-15', 12),
    (222, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000003', 6, 'Lines and Angles', '2027-01-31', 10),
    (223, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000004', 1, 'Matter in Our Surroundings', '2026-05-15', 8),
    (224, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000004', 2, 'Is Matter Around Us Pure', '2026-07-31', 10),
    (225, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000004', 3, 'Atoms and Molecules', '2026-09-15', 9),
    (226, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000004', 4, 'Structure of the Atom', '2026-10-31', 10),
    (227, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000004', 5, 'The Fundamental Unit of Life', '2026-12-15', 12),
    (228, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000004', 6, 'Tissues', '2027-01-31', 10),
    (229, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000005', 1, 'The French Revolution', '2026-05-15', 8),
    (230, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000005', 2, 'Socialism in Europe and the Russian Revolution', '2026-07-31', 10),
    (231, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000005', 3, 'Nazism and the Rise of Hitler', '2026-09-15', 9),
    (232, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000005', 4, 'India: Size and Location', '2026-10-31', 10),
    (233, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000005', 5, 'Physical Features of India', '2026-12-15', 12),
    (234, '5eed0003-0002-4000-8000-000000000012', '5eed0006-0001-4000-8000-000000000005', 6, 'Democracy in the Contemporary World', '2027-01-31', 10),
    (235, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000001', 1, 'A Letter to God', '2026-05-15', 8),
    (236, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000001', 2, 'Nelson Mandela: Long Walk to Freedom', '2026-07-31', 10),
    (237, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000001', 3, 'Two Stories About Flying', '2026-09-15', 9),
    (238, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000001', 4, 'From the Diary of Anne Frank', '2026-10-31', 10),
    (239, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000001', 5, 'Glimpses of India', '2026-12-15', 12),
    (240, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000001', 6, 'Mijbil the Otter', '2027-01-31', 10),
    (241, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000002', 1, 'Surdas Ke Pad', '2026-05-15', 8),
    (242, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000002', 2, 'Tulsidas: Ram Lakshman Parshuram Sanvad', '2026-07-31', 10),
    (243, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000002', 3, 'Dev Ke Savaiye', '2026-09-15', 9),
    (244, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000002', 4, 'Jayshankar Prasad: Aatmakathya', '2026-10-31', 10),
    (245, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000002', 5, 'Utsah Aur Att Nahin Rahi Hai', '2026-12-15', 12),
    (246, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000002', 6, 'Yeh Danturit Muskan', '2027-01-31', 10),
    (247, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000003', 1, 'Real Numbers', '2026-05-15', 8),
    (248, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000003', 2, 'Polynomials', '2026-07-31', 10),
    (249, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000003', 3, 'Pair of Linear Equations in Two Variables', '2026-09-15', 9),
    (250, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000003', 4, 'Quadratic Equations', '2026-10-31', 10),
    (251, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000003', 5, 'Arithmetic Progressions', '2026-12-15', 12),
    (252, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000003', 6, 'Triangles', '2027-01-31', 10),
    (253, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000004', 1, 'Chemical Reactions and Equations', '2026-05-15', 8),
    (254, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000004', 2, 'Acids, Bases and Salts', '2026-07-31', 10),
    (255, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000004', 3, 'Metals and Non-metals', '2026-09-15', 9),
    (256, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000004', 4, 'Carbon and Its Compounds', '2026-10-31', 10),
    (257, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000004', 5, 'Life Processes', '2026-12-15', 12),
    (258, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000004', 6, 'Control and Coordination', '2027-01-31', 10),
    (259, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000005', 1, 'The Rise of Nationalism in Europe', '2026-05-15', 8),
    (260, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000005', 2, 'Nationalism in India', '2026-07-31', 10),
    (261, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000005', 3, 'The Making of a Global World', '2026-09-15', 9),
    (262, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000005', 4, 'Resources and Development', '2026-10-31', 10),
    (263, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000005', 5, 'Forest and Wildlife Resources', '2026-12-15', 12),
    (264, '5eed0003-0002-4000-8000-000000000013', '5eed0006-0001-4000-8000-000000000005', 6, 'Power Sharing', '2027-01-31', 10),
    (265, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000001', 1, 'The Portrait of a Lady', '2026-05-15', 8),
    (266, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000001', 2, 'We''re Not Afraid to Die', '2026-07-31', 10),
    (267, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000001', 3, 'Discovering Tut', '2026-09-15', 9),
    (268, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000001', 4, 'Landscape of the Soul', '2026-10-31', 10),
    (269, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000001', 5, 'The Ailing Planet', '2026-12-15', 12),
    (270, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000001', 6, 'The Browning Version', '2027-01-31', 10),
    (271, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000003', 1, 'Sets', '2026-05-15', 8),
    (272, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000003', 2, 'Relations and Functions', '2026-07-31', 10),
    (273, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000003', 3, 'Trigonometric Functions', '2026-09-15', 9),
    (274, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000003', 4, 'Complex Numbers', '2026-10-31', 10),
    (275, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000003', 5, 'Linear Inequalities', '2026-12-15', 12),
    (276, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000003', 6, 'Permutations and Combinations', '2027-01-31', 10),
    (277, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000006', 1, 'Computer System', '2026-05-15', 8),
    (278, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000006', 2, 'Encoding Schemes and Number System', '2026-07-31', 10),
    (279, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000006', 3, 'Emerging Trends', '2026-09-15', 9),
    (280, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000006', 4, 'Introduction to Problem Solving', '2026-10-31', 10),
    (281, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000006', 5, 'Getting Started with Python', '2026-12-15', 12),
    (282, '5eed0003-0002-4000-8000-000000000014', '5eed0006-0001-4000-8000-000000000006', 6, 'Flow of Control', '2027-01-31', 10),
    (283, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000001', 1, 'The Last Lesson', '2026-05-15', 8),
    (284, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000001', 2, 'Lost Spring', '2026-07-31', 10),
    (285, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000001', 3, 'Deep Water', '2026-09-15', 9),
    (286, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000001', 4, 'The Rattrap', '2026-10-31', 10),
    (287, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000001', 5, 'Indigo', '2026-12-15', 12),
    (288, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000001', 6, 'Poets and Pancakes', '2027-01-31', 10),
    (289, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000003', 1, 'Relations and Functions', '2026-05-15', 8),
    (290, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000003', 2, 'Inverse Trigonometric Functions', '2026-07-31', 10),
    (291, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000003', 3, 'Matrices', '2026-09-15', 9),
    (292, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000003', 4, 'Determinants', '2026-10-31', 10),
    (293, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000003', 5, 'Continuity and Differentiability', '2026-12-15', 12),
    (294, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000003', 6, 'Application of Derivatives', '2027-01-31', 10),
    (295, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000006', 1, 'Exception Handling', '2026-05-15', 8),
    (296, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000006', 2, 'File Handling', '2026-07-31', 10),
    (297, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000006', 3, 'Stack', '2026-09-15', 9),
    (298, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000006', 4, 'Queue', '2026-10-31', 10),
    (299, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000006', 5, 'Sorting', '2026-12-15', 12),
    (300, '5eed0003-0002-4000-8000-000000000015', '5eed0006-0001-4000-8000-000000000006', 6, 'Searching', '2027-01-31', 10)
) AS t(id, grade, subject, seq, title, planned, periods);

INSERT INTO syllabus_coverage (coverage_id, topic_id, section_id, completed_on, completed_by, notes, created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0006-0007-4000-8001-' || lpad(c.id::text, 12, '0'))::uuid, ('5eed0006-0006-4000-8001-' || lpad(c.topic::text, 12, '0'))::uuid, c.section::uuid,
       c.done::date, 'academics@dps.edu.in', NULL, c.done::timestamptz, c.done::timestamptz, 'seed:module06', NULL, 0
FROM (VALUES
    (1, 1, '5eed0003-0003-4000-8000-000000000041', '2026-05-14'),
    (2, 2, '5eed0003-0003-4000-8000-000000000041', '2026-07-30'),
    (3, 3, '5eed0003-0003-4000-8000-000000000041', '2026-09-14'),
    (4, 7, '5eed0003-0003-4000-8000-000000000041', '2026-05-14'),
    (5, 8, '5eed0003-0003-4000-8000-000000000041', '2026-07-30'),
    (6, 9, '5eed0003-0003-4000-8000-000000000041', '2026-09-14'),
    (7, 13, '5eed0003-0003-4000-8000-000000000041', '2026-05-14'),
    (8, 14, '5eed0003-0003-4000-8000-000000000041', '2026-07-30'),
    (9, 15, '5eed0003-0003-4000-8000-000000000041', '2026-09-14'),
    (10, 19, '5eed0003-0003-4000-8000-000000000041', '2026-05-14'),
    (11, 20, '5eed0003-0003-4000-8000-000000000041', '2026-07-30'),
    (12, 21, '5eed0003-0003-4000-8000-000000000041', '2026-09-14'),
    (13, 25, '5eed0003-0003-4000-8000-000000000052', '2026-05-14'),
    (14, 26, '5eed0003-0003-4000-8000-000000000052', '2026-07-30'),
    (15, 27, '5eed0003-0003-4000-8000-000000000052', '2026-09-14'),
    (16, 31, '5eed0003-0003-4000-8000-000000000052', '2026-05-14'),
    (17, 32, '5eed0003-0003-4000-8000-000000000052', '2026-07-30'),
    (18, 33, '5eed0003-0003-4000-8000-000000000052', '2026-09-14'),
    (19, 37, '5eed0003-0003-4000-8000-000000000052', '2026-05-14'),
    (20, 38, '5eed0003-0003-4000-8000-000000000052', '2026-07-30'),
    (21, 39, '5eed0003-0003-4000-8000-000000000052', '2026-09-14'),
    (22, 43, '5eed0003-0003-4000-8000-000000000052', '2026-05-14'),
    (23, 44, '5eed0003-0003-4000-8000-000000000052', '2026-07-30'),
    (24, 45, '5eed0003-0003-4000-8000-000000000052', '2026-09-14'),
    (25, 49, '5eed0003-0003-4000-8000-000000000063', '2026-05-14'),
    (26, 50, '5eed0003-0003-4000-8000-000000000063', '2026-07-30'),
    (27, 55, '5eed0003-0003-4000-8000-000000000063', '2026-05-14'),
    (28, 56, '5eed0003-0003-4000-8000-000000000063', '2026-07-30'),
    (29, 61, '5eed0003-0003-4000-8000-000000000063', '2026-05-14'),
    (30, 62, '5eed0003-0003-4000-8000-000000000063', '2026-07-30'),
    (31, 67, '5eed0003-0003-4000-8000-000000000063', '2026-05-14'),
    (32, 68, '5eed0003-0003-4000-8000-000000000063', '2026-07-30'),
    (33, 73, '5eed0003-0003-4000-8000-000000000071', '2026-05-14'),
    (34, 74, '5eed0003-0003-4000-8000-000000000071', '2026-07-30'),
    (35, 75, '5eed0003-0003-4000-8000-000000000071', '2026-09-14'),
    (36, 79, '5eed0003-0003-4000-8000-000000000071', '2026-05-14'),
    (37, 80, '5eed0003-0003-4000-8000-000000000071', '2026-07-30'),
    (38, 81, '5eed0003-0003-4000-8000-000000000071', '2026-09-14'),
    (39, 85, '5eed0003-0003-4000-8000-000000000071', '2026-05-14'),
    (40, 86, '5eed0003-0003-4000-8000-000000000071', '2026-07-30'),
    (41, 87, '5eed0003-0003-4000-8000-000000000071', '2026-09-14'),
    (42, 91, '5eed0003-0003-4000-8000-000000000071', '2026-05-14'),
    (43, 92, '5eed0003-0003-4000-8000-000000000071', '2026-07-30'),
    (44, 93, '5eed0003-0003-4000-8000-000000000071', '2026-09-14'),
    (45, 97, '5eed0003-0003-4000-8000-000000000082', '2026-05-14'),
    (46, 98, '5eed0003-0003-4000-8000-000000000082', '2026-07-30'),
    (47, 99, '5eed0003-0003-4000-8000-000000000082', '2026-09-14'),
    (48, 103, '5eed0003-0003-4000-8000-000000000082', '2026-05-14'),
    (49, 104, '5eed0003-0003-4000-8000-000000000082', '2026-07-30'),
    (50, 105, '5eed0003-0003-4000-8000-000000000082', '2026-09-14'),
    (51, 109, '5eed0003-0003-4000-8000-000000000082', '2026-05-14'),
    (52, 110, '5eed0003-0003-4000-8000-000000000082', '2026-07-30'),
    (53, 111, '5eed0003-0003-4000-8000-000000000082', '2026-09-14'),
    (54, 115, '5eed0003-0003-4000-8000-000000000082', '2026-05-14'),
    (55, 116, '5eed0003-0003-4000-8000-000000000082', '2026-07-30'),
    (56, 117, '5eed0003-0003-4000-8000-000000000082', '2026-09-14'),
    (57, 121, '5eed0003-0003-4000-8000-000000000091', '2026-05-14'),
    (58, 122, '5eed0003-0003-4000-8000-000000000091', '2026-07-30'),
    (59, 123, '5eed0003-0003-4000-8000-000000000091', '2026-09-14'),
    (60, 121, '5eed0003-0003-4000-8000-000000000092', '2026-05-14'),
    (61, 122, '5eed0003-0003-4000-8000-000000000092', '2026-07-30'),
    (62, 121, '5eed0003-0003-4000-8000-000000000093', '2026-05-14'),
    (63, 122, '5eed0003-0003-4000-8000-000000000093', '2026-07-30'),
    (64, 123, '5eed0003-0003-4000-8000-000000000093', '2026-09-14'),
    (65, 127, '5eed0003-0003-4000-8000-000000000091', '2026-05-14'),
    (66, 128, '5eed0003-0003-4000-8000-000000000091', '2026-07-30'),
    (67, 129, '5eed0003-0003-4000-8000-000000000091', '2026-09-14'),
    (68, 127, '5eed0003-0003-4000-8000-000000000092', '2026-05-14'),
    (69, 128, '5eed0003-0003-4000-8000-000000000092', '2026-07-30'),
    (70, 127, '5eed0003-0003-4000-8000-000000000093', '2026-05-14'),
    (71, 128, '5eed0003-0003-4000-8000-000000000093', '2026-07-30'),
    (72, 129, '5eed0003-0003-4000-8000-000000000093', '2026-09-14'),
    (73, 133, '5eed0003-0003-4000-8000-000000000091', '2026-05-14'),
    (74, 134, '5eed0003-0003-4000-8000-000000000091', '2026-07-30'),
    (75, 135, '5eed0003-0003-4000-8000-000000000091', '2026-09-14'),
    (76, 133, '5eed0003-0003-4000-8000-000000000092', '2026-05-14'),
    (77, 134, '5eed0003-0003-4000-8000-000000000092', '2026-07-30'),
    (78, 133, '5eed0003-0003-4000-8000-000000000093', '2026-05-14'),
    (79, 134, '5eed0003-0003-4000-8000-000000000093', '2026-07-30'),
    (80, 135, '5eed0003-0003-4000-8000-000000000093', '2026-09-14'),
    (81, 139, '5eed0003-0003-4000-8000-000000000091', '2026-05-14'),
    (82, 140, '5eed0003-0003-4000-8000-000000000091', '2026-07-30'),
    (83, 141, '5eed0003-0003-4000-8000-000000000091', '2026-09-14'),
    (84, 139, '5eed0003-0003-4000-8000-000000000092', '2026-05-14'),
    (85, 140, '5eed0003-0003-4000-8000-000000000092', '2026-07-30'),
    (86, 139, '5eed0003-0003-4000-8000-000000000093', '2026-05-14'),
    (87, 140, '5eed0003-0003-4000-8000-000000000093', '2026-07-30'),
    (88, 141, '5eed0003-0003-4000-8000-000000000093', '2026-09-14'),
    (89, 145, '5eed0003-0003-4000-8000-000000000101', '2026-05-14'),
    (90, 146, '5eed0003-0003-4000-8000-000000000101', '2026-07-30'),
    (91, 147, '5eed0003-0003-4000-8000-000000000101', '2026-09-14'),
    (92, 151, '5eed0003-0003-4000-8000-000000000101', '2026-05-14'),
    (93, 152, '5eed0003-0003-4000-8000-000000000101', '2026-07-30'),
    (94, 153, '5eed0003-0003-4000-8000-000000000101', '2026-09-14'),
    (95, 157, '5eed0003-0003-4000-8000-000000000101', '2026-05-14'),
    (96, 158, '5eed0003-0003-4000-8000-000000000101', '2026-07-30'),
    (97, 159, '5eed0003-0003-4000-8000-000000000101', '2026-09-14'),
    (98, 163, '5eed0003-0003-4000-8000-000000000101', '2026-05-14'),
    (99, 164, '5eed0003-0003-4000-8000-000000000101', '2026-07-30'),
    (100, 165, '5eed0003-0003-4000-8000-000000000101', '2026-09-14'),
    (101, 169, '5eed0003-0003-4000-8000-000000000101', '2026-05-14'),
    (102, 170, '5eed0003-0003-4000-8000-000000000101', '2026-07-30'),
    (103, 171, '5eed0003-0003-4000-8000-000000000101', '2026-09-14'),
    (104, 175, '5eed0003-0003-4000-8000-000000000112', '2026-05-14'),
    (105, 176, '5eed0003-0003-4000-8000-000000000112', '2026-07-30'),
    (106, 177, '5eed0003-0003-4000-8000-000000000112', '2026-09-14'),
    (107, 181, '5eed0003-0003-4000-8000-000000000112', '2026-05-14'),
    (108, 182, '5eed0003-0003-4000-8000-000000000112', '2026-07-30'),
    (109, 183, '5eed0003-0003-4000-8000-000000000112', '2026-09-14'),
    (110, 187, '5eed0003-0003-4000-8000-000000000112', '2026-05-14'),
    (111, 188, '5eed0003-0003-4000-8000-000000000112', '2026-07-30'),
    (112, 189, '5eed0003-0003-4000-8000-000000000112', '2026-09-14'),
    (113, 193, '5eed0003-0003-4000-8000-000000000112', '2026-05-14'),
    (114, 194, '5eed0003-0003-4000-8000-000000000112', '2026-07-30'),
    (115, 195, '5eed0003-0003-4000-8000-000000000112', '2026-09-14'),
    (116, 199, '5eed0003-0003-4000-8000-000000000112', '2026-05-14'),
    (117, 200, '5eed0003-0003-4000-8000-000000000112', '2026-07-30'),
    (118, 201, '5eed0003-0003-4000-8000-000000000112', '2026-09-14'),
    (119, 205, '5eed0003-0003-4000-8000-000000000121', '2026-05-14'),
    (120, 206, '5eed0003-0003-4000-8000-000000000121', '2026-07-30'),
    (121, 207, '5eed0003-0003-4000-8000-000000000121', '2026-09-14'),
    (122, 205, '5eed0003-0003-4000-8000-000000000122', '2026-05-14'),
    (123, 206, '5eed0003-0003-4000-8000-000000000122', '2026-07-30'),
    (124, 207, '5eed0003-0003-4000-8000-000000000122', '2026-09-14'),
    (125, 205, '5eed0003-0003-4000-8000-000000000123', '2026-05-14'),
    (126, 206, '5eed0003-0003-4000-8000-000000000123', '2026-07-30'),
    (127, 211, '5eed0003-0003-4000-8000-000000000121', '2026-05-14'),
    (128, 212, '5eed0003-0003-4000-8000-000000000121', '2026-07-30'),
    (129, 213, '5eed0003-0003-4000-8000-000000000121', '2026-09-14'),
    (130, 211, '5eed0003-0003-4000-8000-000000000122', '2026-05-14'),
    (131, 212, '5eed0003-0003-4000-8000-000000000122', '2026-07-30'),
    (132, 213, '5eed0003-0003-4000-8000-000000000122', '2026-09-14'),
    (133, 211, '5eed0003-0003-4000-8000-000000000123', '2026-05-14'),
    (134, 212, '5eed0003-0003-4000-8000-000000000123', '2026-07-30'),
    (135, 217, '5eed0003-0003-4000-8000-000000000121', '2026-05-14'),
    (136, 218, '5eed0003-0003-4000-8000-000000000121', '2026-07-30'),
    (137, 219, '5eed0003-0003-4000-8000-000000000121', '2026-09-14'),
    (138, 217, '5eed0003-0003-4000-8000-000000000122', '2026-05-14'),
    (139, 218, '5eed0003-0003-4000-8000-000000000122', '2026-07-30'),
    (140, 219, '5eed0003-0003-4000-8000-000000000122', '2026-09-14'),
    (141, 217, '5eed0003-0003-4000-8000-000000000123', '2026-05-14'),
    (142, 218, '5eed0003-0003-4000-8000-000000000123', '2026-07-30'),
    (143, 223, '5eed0003-0003-4000-8000-000000000121', '2026-05-14'),
    (144, 224, '5eed0003-0003-4000-8000-000000000121', '2026-07-30'),
    (145, 225, '5eed0003-0003-4000-8000-000000000121', '2026-09-14'),
    (146, 223, '5eed0003-0003-4000-8000-000000000122', '2026-05-14'),
    (147, 224, '5eed0003-0003-4000-8000-000000000122', '2026-07-30'),
    (148, 225, '5eed0003-0003-4000-8000-000000000122', '2026-09-14'),
    (149, 223, '5eed0003-0003-4000-8000-000000000123', '2026-05-14'),
    (150, 224, '5eed0003-0003-4000-8000-000000000123', '2026-07-30'),
    (151, 229, '5eed0003-0003-4000-8000-000000000121', '2026-05-14'),
    (152, 230, '5eed0003-0003-4000-8000-000000000121', '2026-07-30'),
    (153, 231, '5eed0003-0003-4000-8000-000000000121', '2026-09-14'),
    (154, 229, '5eed0003-0003-4000-8000-000000000122', '2026-05-14'),
    (155, 230, '5eed0003-0003-4000-8000-000000000122', '2026-07-30'),
    (156, 231, '5eed0003-0003-4000-8000-000000000122', '2026-09-14'),
    (157, 229, '5eed0003-0003-4000-8000-000000000123', '2026-05-14'),
    (158, 230, '5eed0003-0003-4000-8000-000000000123', '2026-07-30'),
    (159, 235, '5eed0003-0003-4000-8000-000000000131', '2026-05-14'),
    (160, 236, '5eed0003-0003-4000-8000-000000000131', '2026-07-30'),
    (161, 237, '5eed0003-0003-4000-8000-000000000131', '2026-09-14'),
    (162, 241, '5eed0003-0003-4000-8000-000000000131', '2026-05-14'),
    (163, 242, '5eed0003-0003-4000-8000-000000000131', '2026-07-30'),
    (164, 243, '5eed0003-0003-4000-8000-000000000131', '2026-09-14'),
    (165, 247, '5eed0003-0003-4000-8000-000000000131', '2026-05-14'),
    (166, 248, '5eed0003-0003-4000-8000-000000000131', '2026-07-30'),
    (167, 249, '5eed0003-0003-4000-8000-000000000131', '2026-09-14'),
    (168, 253, '5eed0003-0003-4000-8000-000000000131', '2026-05-14'),
    (169, 254, '5eed0003-0003-4000-8000-000000000131', '2026-07-30'),
    (170, 255, '5eed0003-0003-4000-8000-000000000131', '2026-09-14'),
    (171, 259, '5eed0003-0003-4000-8000-000000000131', '2026-05-14'),
    (172, 260, '5eed0003-0003-4000-8000-000000000131', '2026-07-30'),
    (173, 261, '5eed0003-0003-4000-8000-000000000131', '2026-09-14'),
    (174, 265, '5eed0003-0003-4000-8000-000000000142', '2026-05-14'),
    (175, 266, '5eed0003-0003-4000-8000-000000000142', '2026-07-30'),
    (176, 267, '5eed0003-0003-4000-8000-000000000142', '2026-09-14'),
    (177, 271, '5eed0003-0003-4000-8000-000000000142', '2026-05-14'),
    (178, 272, '5eed0003-0003-4000-8000-000000000142', '2026-07-30'),
    (179, 273, '5eed0003-0003-4000-8000-000000000142', '2026-09-14'),
    (180, 277, '5eed0003-0003-4000-8000-000000000142', '2026-05-14'),
    (181, 278, '5eed0003-0003-4000-8000-000000000142', '2026-07-30'),
    (182, 279, '5eed0003-0003-4000-8000-000000000142', '2026-09-14'),
    (183, 283, '5eed0003-0003-4000-8000-000000000151', '2026-05-14'),
    (184, 284, '5eed0003-0003-4000-8000-000000000151', '2026-07-30'),
    (185, 285, '5eed0003-0003-4000-8000-000000000151', '2026-09-14'),
    (186, 283, '5eed0003-0003-4000-8000-000000000153', '2026-05-14'),
    (187, 284, '5eed0003-0003-4000-8000-000000000153', '2026-07-30'),
    (188, 289, '5eed0003-0003-4000-8000-000000000151', '2026-05-14'),
    (189, 290, '5eed0003-0003-4000-8000-000000000151', '2026-07-30'),
    (190, 291, '5eed0003-0003-4000-8000-000000000151', '2026-09-14'),
    (191, 289, '5eed0003-0003-4000-8000-000000000153', '2026-05-14'),
    (192, 290, '5eed0003-0003-4000-8000-000000000153', '2026-07-30'),
    (193, 295, '5eed0003-0003-4000-8000-000000000151', '2026-05-14'),
    (194, 296, '5eed0003-0003-4000-8000-000000000151', '2026-07-30'),
    (195, 297, '5eed0003-0003-4000-8000-000000000151', '2026-09-14'),
    (196, 295, '5eed0003-0003-4000-8000-000000000153', '2026-05-14'),
    (197, 296, '5eed0003-0003-4000-8000-000000000153', '2026-07-30')
) AS c(id, topic, section, done);

-- ---------------------------------------------------------------------------------------------------
-- 5. Labs (US-06011)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO lab_rooms (room_id, tenant_id, branch_id, room_name, room_type, capacity, is_active, created_at, updated_at, created_by, updated_by, version)
VALUES ('5eed0006-0008-4000-8000-000000000001', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'Computer Lab 1', 'COMPUTER_LAB', 40, true,
        TIMESTAMPTZ '2026-03-26 05:00:00+00', TIMESTAMPTZ '2026-03-26 05:00:00+00', 'seed:module06', NULL, 0),
       ('5eed0006-0008-4000-8000-000000000002', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'Science Lab', 'SCIENCE_LAB', 24, true,
        TIMESTAMPTZ '2026-03-26 05:00:00+00', TIMESTAMPTZ '2026-03-26 05:00:00+00', 'seed:module06', NULL, 0);

COMMIT;
