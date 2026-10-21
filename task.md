# Task Execution Record: Module 06 — Academics, Timetable & LMS (first release)

Execution of the `BRD_API_Generate` skill for **Module 06** based on [`story/module6/School_ERP_Module_06_Academics_LMS_1000.md`](../story/module6/School_ERP_Module_06_Academics_LMS_1000.md), [`module6.sql`](../story/module6/module6.sql) and [`module6.md`](../story/module6/module6.md).

---

## 1. Input Specification & Target Microservice Resolution

- **User Stories**: `US-06000` – `US-06019`, all 20 base stories (scenario 1 of each; the BRD's 1,000 entries are 50 variations of these 20 stories with the eight actors rotated)
- **Module**: `06` — Academic Planning, Timetable Optimization & LMS
- **Target Microservice**: `academicservice`
- **Base Package**: `com.kvs.academic`
- **Allocated Server Port**: `8087`, context path `/academicservice`, test port `18087` (next free port, as in the skill table)
- **Base path**: `/api/v1/lms`, not `/api/v1/academics`: studentservice already serves `/api/v1/academic`, and the UI proxy matches path prefixes, so any `/api/v1/academic…` path would reach studentservice
- **Database**: shared PostgreSQL database `authservice`, tables created by Hibernate `ddl-auto: update`
- **Service Lifecycle Status**:
  - [ ] Existing Microservice
  - [x] New Microservice (scaffolded via Step 0 at the product owner's request, 19 Oct 2026)
- **Read model**: `StudentMaster`, `Grade`, `Section`, `AcademicSession` map studentservice's tables read-only (rosters, class names, academic years)
- **Reference Architecture**: bootstrap, security, tenant guard, access-rules endpoint and audit service copied from `examservice`; request exception handler from `admissionservice`

---

## 2. Requirement Extraction (from BRD)

| Story | Sub-module (BRD) | Actor (scenario 1) → Role | Action |
|---|---|---|---|
| US-06000 | Master Bell Schedule & Period Duration Configuration | Academic Coordinator → `ACADEMIC_COORDINATOR` | Configure daily period schedules, recess times and master bell patterns |
| US-06001 | Subject-Teacher Allocations & Weekly Workload Caps | Timetable In-Charge → `TIMETABLE_INCHARGE` | Assign faculty workload hours based on weekly maximum teaching caps |
| US-06002 | Constraint-Based Conflict-Free Timetable Generator | Subject Teacher (BRD) / Timetable In-Charge generates | Generate conflict-free class timetables using automated constraint engines |
| US-06003 | Morning Staff Absence & Proxy Substitution Optimizer | Substitution In-Charge → `SUBSTITUTION_INCHARGE` | Assign proxy substitute teachers automatically during morning roll call |
| US-06004 | Syllabus Coverage Tracking & Digital Lesson Planning | Student (BRD) / Academic Coordinator plans, teachers mark coverage | Track syllabus completion against annual academic calendars |
| US-06005 | E-Learning Asset Upload (PDFs, Video Lectures, SCORM) | E-Learning Admin → `ELEARNING_ADMIN` | Upload digital learning assets, lecture videos and reference e-books |
| US-06006 | AI-Driven MCQ Question Bank Generator | Parent (BRD) / Subject Teacher → `SUBJECT_TEACHER` | Auto-generate multiple-choice questions from chapter text |
| US-06007 | Online Quiz Proctoring, Tab-Switch Alerts & Timers | Principal → `SCHOOL_PRINCIPAL` (report); parent app takes the quiz | Proctored online assessments with tab-switch detection |
| US-06008 | Timed Homework Submission & Digital Valuation | Academic Coordinator (BRD) / Subject Teacher | Evaluate homework digitally with annotations and marks |
| US-06009 | Gamified Learning Leaderboards & Skill Analytics | Timetable In-Charge (BRD) / teachers, parent | Student skill analytics and topic mastery radar charts |
| US-06010 | Master Bell Schedule (BRD grouping) | Subject Teacher | Publish daily homework to parent app dashboards |
| US-06011 | Subject-Teacher Allocations (BRD grouping) | Substitution In-Charge (BRD) / Subject Teacher books, Coordinator registers rooms | Computer and science lab reservations |
| US-06012 | Conflict-Free Timetable Generator (BRD grouping) | Student (BRD) / Subject Teacher | Schedule remedial classes for students needing support |
| US-06013 | Morning Staff Absence (BRD grouping) | E-Learning Admin (BRD) / teacher writes, Principal reviews | Lesson plan submissions and principal approval |
| US-06014 | Syllabus Coverage (BRD grouping) | Parent (BRD) / Subject Teacher | Quiz countdown timers and randomized question order |
| US-06015 | E-Learning Asset Upload (BRD grouping) | Principal | Export master faculty timetables to PDF for staff room noticeboards |
| US-06016 | AI MCQ Generator (BRD grouping) | Academic Coordinator | Overdue homework alerts |
| US-06017 | Quiz Proctoring (BRD grouping) | Timetable In-Charge (BRD) / teachers, E-Learning Admin | Skill badges for top performers on LMS leaderboards |
| US-06018 | Homework Valuation (BRD grouping) | Subject Teacher | Video lecture watch time and completion rates |
| US-06019 | Gamified Learning (BRD grouping) | Substitution In-Charge (BRD) / Coordinator, Principal | Archive lesson plans and syllabus for faculty reference |

The BRD rotates the eight actors (Academic Coordinator, Timetable In-Charge, Subject Teacher, Substitution In-Charge, Student, E-Learning Admin, Parent, Principal) and places stories 10-19 under unrelated sub-module headings. Roles were assigned to the people who do that work in a school ([`AcademicRoles`](src/main/java/com/kvs/academic/config/AcademicRoles.java)). Students have no login yet, so the parent app acts for the child.

### Acceptance Criteria (each story)
- [x] **AC-1 (Privileges)**: only the story's roles (plus `SUPER_ADMIN`) may act; parents only for their own children; teachers edit only their own lesson plans, homework evaluations and bookings
- [x] **AC-2 (Execution/Input)**: endpoints accept the documented payloads (JSON; multipart for asset files)
- [x] **AC-3 (Validation)**: invalid input → `400 VALIDATION_FAILED` / `MALFORMED_REQUEST` / `BAD_REQUEST` with a domain code where useful
- [x] **AC-4 (Persistence)**: each operation is one transaction
- [x] **AC-5 (Audit Trail)**: `audit_logs` rows such as `BELL_SCHEDULE_CREATED`, `SUBJECT_ALLOCATED`, `TIMETABLE_GENERATED`, `PROXIES_AUTO_ASSIGNED`, `SYLLABUS_TOPIC_COVERED`, `LEARNING_ASSET_UPLOADED`, `QUIZ_QUESTIONS_GENERATED`, `QUIZ_ATTEMPT_AUTO_SUBMITTED`, `HOMEWORK_EVALUATED`, `HOMEWORK_OVERDUE_ALERTS_SENT`, `SKILL_BADGES_AWARDED`, `LAB_RESERVED`, `REMEDIAL_SESSION_SCHEDULED`, `LESSON_PLAN_APPROVED`, `FACULTY_TIMETABLE_EXPORTED`, `ACADEMIC_YEAR_ARCHIVED`
- [x] **AC-6 (Security)**: `401` without a token, `403` for other roles, `404` for another institution's records

---

## 3. API Contract & Architectural Design

Full contract: [`wiki/api-docs/School_ERP_Module_06_Academics_LMS_API_Documentation.md`](wiki/api-docs/School_ERP_Module_06_Academics_LMS_API_Documentation.md). **80 endpoints plus `GET /api/v1/lms/access-rules`**, all under `/api/v1/lms`. The roles below were read from the running service.

| Story | Endpoints | Write roles |
|---|---|---|
| US-06000 | `POST/GET bell-schedules`, `GET bell-schedules/{id}`, `POST bell-schedules/{id}/activate` | ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SCHOOL_PRINCIPAL |
| US-06001 | `POST/GET subjects`, `PUT/GET workloads`, `POST/GET allocations`, `DELETE allocations/{id}` | TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR |
| US-06002 | `POST timetables/generate`, `PUT timetables/entries/{id}`, `GET timetables/sections/{id}`, `…/teachers/{id}`, `…/students/{id}` | TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR |
| US-06003 | `POST/GET substitutions/absences`, `POST substitutions/auto-assign`, `GET substitutions`, `PUT substitutions/{id}` | SUBSTITUTION_INCHARGE, TIMETABLE_INCHARGE, VICE_PRINCIPAL |
| US-06004 | `PUT/GET syllabus/plans`, `POST syllabus/topics/{id}/coverage`, `GET syllabus/coverage`, `GET syllabus/students/{id}/coverage` | plan: ACADEMIC_COORDINATOR; coverage: SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR |
| US-06005 | `POST assets` (multipart), `POST assets/links`, `GET assets`, `GET assets/{id}/content` | ELEARNING_ADMIN, SUBJECT_TEACHER |
| US-06006 | `POST/GET quizzes`, `GET quizzes/{id}`, `POST quizzes/{id}/questions/generate`, `POST/PUT/DELETE quizzes/{id}/questions…` | SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN |
| US-06014 | `PUT quizzes/{id}/settings`, `POST quizzes/{id}/publish` | same as US-06006 |
| US-06007 | `POST quizzes/{id}/attempts`, `GET attempts/{id}`, `PUT attempts/{id}/answers`, `POST attempts/{id}/events`, `POST attempts/{id}/submit`, `GET quizzes/{id}/proctoring-report` | attempt: APPLICANT_PARENT, SUBJECT_TEACHER; report: principal, VP, coordinator, teacher, e-learning admin |
| US-06018 | `POST assets/{id}/watch-progress`, `GET assets/{id}/watch-report` | progress: APPLICANT_PARENT |
| US-06010 | `POST/GET homework`, `POST homework/{id}/submissions`, `GET homework/students/{id}` | publish: SUBJECT_TEACHER, CLASS_TEACHER; submit: parent (or teacher for paper work) |
| US-06008 | `GET homework/{id}/submissions`, `PUT homework/submissions/{id}/evaluation` | SUBJECT_TEACHER (own homework), CLASS_TEACHER, ACADEMIC_COORDINATOR |
| US-06016 | `POST homework/overdue-alerts/run`, `GET homework/overdue-alerts` | ACADEMIC_COORDINATOR, CLASS_TEACHER, SUBJECT_TEACHER |
| US-06009 | `GET analytics/students/{id}/skills`, `GET analytics/sections/{id}/skills` | read only (parent: own child) |
| US-06017 | `GET leaderboards/sections/{id}`, `POST badges/award-top`, `POST badges`, `GET badges/students/{id}` | SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN |
| US-06012 | `GET remedial/candidates`, `POST/GET remedial-sessions`, `GET remedial-sessions/students/{id}` | SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR |
| US-06013 | `POST/GET lesson-plans`, `PUT lesson-plans/{id}`, `POST lesson-plans/{id}/submit`, `POST lesson-plans/{id}/review`, `GET lesson-plans/compliance` | write: SUBJECT_TEACHER, CLASS_TEACHER; review: SCHOOL_PRINCIPAL, VICE_PRINCIPAL, ACADEMIC_COORDINATOR |
| US-06011 | `POST/GET labs`, `POST/GET labs/{id}/reservations`, `POST labs/reservations/{id}/cancel` | rooms: coordinator, timetable in-charge; book: teachers, substitution / timetable in-charge, coordinator |
| US-06015 | `GET timetables/faculty/pdf` | SCHOOL_PRINCIPAL, VICE_PRINCIPAL, TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUBSTITUTION_INCHARGE |
| US-06019 | `POST archives`, `GET archives/lesson-plans`, `GET archives/syllabus` | ACADEMIC_COORDINATOR, SCHOOL_PRINCIPAL |

### Component Mapping
- **Entities (24 tables)**: `Subject`, `BellSchedule`, `BellPeriod`, `FacultyWorkload`, `SubjectAllocation`, `TimetableEntry` (`master_timetables`), `FacultyAbsence`, `ProxySubstitution` (`faculty_proxy_substitutions`), `SyllabusTopic`, `SyllabusCoverage`, `LearningAsset`, `VideoWatchProgress`, `Quiz` (`lms_quizzes`), `QuizQuestion`, `QuizAttempt`, `ProctorEvent`, `HomeworkAssignment`, `HomeworkSubmission`, `HomeworkAlert`, `SkillBadge`, `LabRoom`, `LabReservation`, `RemedialSession`, `LessonPlan`; plus `AuditLog` and the read model
- **Services**: `SchedulingService` (+ `FacultyTimetablePdfRenderer`), `SubstitutionService`, `SyllabusService`, `LessonPlanService`, `LearningContentService` (+ `AssetStore`), `QuizService` (+ `McqGenerator`), `HomeworkService`, `LearnerInsightsService`, `LabService`, `AcademicContext`, `TenantAccessGuard`, `AcademicAuditService`, `JsonText`
- **Controllers**: `TimetableController`, `SubstitutionController`, `CurriculumController`, `LearningContentController`, `QuizController`, `HomeworkController`, `LearnerInsightsController`, `LabController`, `AccessRuleController`

### Design notes
- **Timetable generator (US-06002)** is a deterministic greedy constraint placer, not a solver. It places allocations, largest first, under hard constraints: one period per class slot; no teacher overlap, checked by time so other bell schedules count too; daily and weekly caps; at most N periods of a subject a day. It spreads each subject over the week. It never forces a period: anything unplaced is reported with the reason. Regenerating replaces the sections' timetables and **clears upcoming proxy substitutions of the replaced periods** (`substitutionsCleared`); past ones stay as history.
- **Proxy optimiser (US-06003)**: the candidate must be present, free (not teaching and not already covering at that time) and under the daily cap. Ranking: qualified for the subject, already teaches the class, fewest proxies in 7 days, lightest day, then name. Reruns keep existing assignments.
- **Question generator (US-06006)** is built in and rule-based, with no external AI service (like the WhatsApp and board stand-ins in Module 02). It blanks one key term per sentence (a proper noun, a number or a long word) and draws distractors of the same kind; years get nearby years. Output is deterministic.
- **Proctoring (US-06007)**: each attempt stores its own question and option order, and answers are stored against the original option. Tab switches and window blurs beyond the limit auto-submit and flag the attempt. Copy/paste flags it. A missed deadline (plus 30 s grace) closes the attempt as `TIMED_OUT`. The answer key is never sent to the attempt.
- **Watch time (US-06018)**: only continuous playback moves the furthest point, so skipping ahead does not count; 90% counts as completed.
- **Teachers** are identified by their authservice user id (the token's `userId` claim). The faculty workload register (US-06001) is the list of teachers the scheduler knows.
- **Audit writes** run in their own transaction (`TransactionTemplate`, `REQUIRES_NEW`). A failed audit insert can never roll back the request, and read-only operations (the PDF export) are still audited.
- **Schema vs `module6.sql`**: the questions are stored in `lms_quiz_questions` rather than in `question_bank_json`, so they can be edited and analysed one by one. `teacher_id` is the authservice user id (text). The added tables are listed above.

---

## 4. Implementation Checklist

### Step 0: Microservice Scaffolding
- [x] `build.gradle` (Spring Boot 3.2.5, Java 21, ms-core, PDFBox 3.0.3), `settings.gradle`, Gradle wrapper, `.gitignore`
- [x] `application.yml`: port `${SERVER_PORT:8087}`, context path `${SERVER_CONTEXT_PATH:/academicservice}`, multipart up to 500 MB, asset store `academic.asset-store.*`; test `application.yml` on H2
- [x] `AcademicServiceApplication`, `WebSecurityConfig`, `JpaAuditConfig`, `AcademicRequestExceptionHandler` — compiled

### Steps 1–6
- [x] 24 Module 06 entities (+ `AuditLog` and the 4 read-model entities), 92 DTOs, 29 repositories, services and controllers (above)
- [x] Integration tests for each story: 201/200, 400, 401, 403, 404, 409 and the trace header (§5)

### Step 7: Postman
- [x] [`postman/School_ERP_AcademicService.postman_collection.json`](postman/School_ERP_AcademicService.postman_collection.json): a sign-in folder (8 seeded roles, 401, trace id) and one folder per story, **114 requests in 21 folders**. Variables are validated, and ids are chained in run order (quiz → attempt, homework → evaluation, …).

### UI mapping
- [x] Vite proxy `'/api/v1/lms' → ACADEMIC_SERVICE_URL || http://localhost:8087/academicservice` (`frontend/vite.config.js`)
- [x] **Teaching & learning** page (`/teaching`, `frontend/src/components/TeachingLive.jsx`): timetable grid (class / teacher), generate timetables, faculty PDF, workload, cover (absence, auto-assign, reassign), syllabus coverage (mark taught), homework (set, roster, evaluate), lesson plans (write, submit, approve / return, compliance), labs (book, cancel); screen-by-screen map in `frontend/docs/academicservice-api-mapping.md`
- [x] test-ui regression `scripts/cases/academicservice.json` (LMS-01..45, 45/45) and browser checklist `references/academicservice-ui-checklist.md` (UL-01..24)
- [ ] Screens for set-up, e-learning assets, quizzes, analytics / badges / remedial, overdue alerts, archive and the parent app (API only for now)

### Step 8: API Wiki
- [x] [`wiki/api-docs/School_ERP_Module_06_Academics_LMS_API_Documentation.md`](wiki/api-docs/School_ERP_Module_06_Academics_LMS_API_Documentation.md): conventions, role map, one `{expand}` per story (20) with rules, endpoint roles, request field tables, examples and response fields, plus the full endpoint index

### Seed
- [x] [`module06_academic_seed.sql`](src/main/resources/db/seed/module06_academic_seed.sql) (run after Modules 01 and 03):
  - roles `ACADEMIC_COORDINATOR`, `TIMETABLE_INCHARGE`, `SUBSTITUTION_INCHARGE`, `ELEARNING_ADMIN`;
  - accounts `coordinator_sunita`, `timetable_rahul`, `substitution_meenal`, `elearning_farah`, and teachers `teacher_kavya`, `teacher_imran`, `teacher_deepa`, `teacher_arjun`, `teacher_sana` (`Password@123`);
  - 6 subjects; the "DPS North regular" bell schedule (Mon–Sat, 8 periods);
  - 7 workload caps; 10 allocations for Class 6-A and 6-C;
  - the Class 6 SST syllabus (6 chapters; 6-A has covered 3, 6-C 1); 2 labs.
  - Run twice: counts unchanged (9 accounts, 6 subjects, 11 slots, 7 workloads, 10 allocations, 6 chapters, 4 coverage rows, 2 labs).

---

## 5. Verification & Quality Gates

```bash
cd academicservice
.\gradlew.bat test
```

| Test class | Tests | Covers |
|---|---|---|
| `SchedulingControllerIntegrationTest` | 9 | US-06000 totals, overlap, 401/403/404, other tenant; US-06001 caps, qualification, cap lowering, duplicates; US-06002 conflict-free generation, unplaced report, moves (taken slot, double booking, not qualified), parent view; US-06003 auto-assign, rerun, manual reassignment, cancel, all-absent → UNCOVERED, **regeneration clears upcoming proxies**; US-06015 PDF pages and text |
| `LmsControllerIntegrationTest` | 5 | US-06005 PDF/MP4/SCORM uploads, signature and manifest checks, size, parent rules; US-06018 continuous playback vs skip ahead, report; US-06006 generation and validation; US-06014 rules lock; US-06007 parent attempt, resume, attempts exhausted, auto-submit on tab switches, TIMED_OUT, proctoring report |
| `HomeworkInsightsControllerIntegrationTest` | 5 | US-06010 publish/submit/dashboard; US-06008 roster, evaluation with annotations, other teacher 403, no resubmit; US-06016 alerts once per student, late flag; US-06009 radar, levels, section averages; US-06017 points, shared ranks, top-N badges, duplicates; US-06012 candidates, TEACHER_BUSY, STUDENTS_IN_CLASS, coordinator for another teacher |
| `CurriculumControllerIntegrationTest` | 5 | US-06004 BEHIND/ON_TRACK, overdue, PLAN_IN_USE, order/year checks; US-06011 overlap, teacher double booking, capacity, cancel rules; US-06013 full review flow, own plans, no self-review, compliance with absences; US-06019 YEAR_NOT_CLOSED, read-only archive, search |
| `AcademicDayEndToEndTest` | 1 | One school day: bell schedule → 6 teachers → 2 timetables (62 periods) → absence → every period covered by a qualified proxy → 6-page PDF → parent timetable |
| `AccessRuleControllerIntegrationTest` | 2 | endpoint → roles listing; 401 |
| `McqGeneratorTest` | 4 | blanks and options, determinism, year distractors, no key terms |
| **Total** | **31** | **0 failures, 0 errors** (19 Oct 2026) |

### Live end-to-end run (test stack, PostgreSQL, real authservice logins)
The school-day flow ran on `http://localhost:18087/academicservice` with the seeded accounts (scratchpad script `school-day-e2e.mjs`): **18/18 checks passed**, twice in a row.

| Step | Account | Result |
|---|---|---|
| US-06000 read the active schedule | coordinator_sunita | 8 periods/day, 48/week, 07:50–14:20 |
| US-06001 workloads; SST to Neha | timetable_rahul | Neha 14/30 (46.7%); `409 TEACHER_NOT_QUALIFIED` |
| US-06002 generate 6-A and 6-C | timetable_rahul (subjectteacher_neha → 403) | 62/62 periods placed; 7 teacher timetables, 0 clashes; ≤ 2 periods of a subject a day |
| US-06003 Neha absent Mon 26 Oct; auto-assign | substitution_meenal | 2/2 periods covered by Sana Qureshi (teaches MATH, free, fewest proxies); rerun keeps them |
| US-06015 faculty timetable PDF | principal_anita (teacher → 403) | 7 pages (PDFBox), one per teacher, cells match allocations |
| Parent app, US-06004 | parent_rohit | Aarav's 6-A timetable (31 periods); another family's child 403; SST 50% vs 50% (ON_TRACK); 6-C 16.7% vs 50% (BEHIND) |

The live run found two bugs that the H2 tests had missed. Both are fixed and covered:
1. **PDF export returned 500 on PostgreSQL.** Its audit insert ran inside a read-only transaction (H2 ignores read-only). Audit writes now use their own transaction.
2. **Regenerating a timetable orphaned upcoming substitutions.** This skewed the fairness counts ("3 proxies in the last 7 days" for 2). Regeneration now clears them and reports `substitutionsCleared`. A test was added, and the orphan rows from the early runs were deleted.

Data left in the shared database:
- the seed;
- the generated 6-A / 6-C timetables;
- Neha's absence on 26 Oct 2026 with its 2 proxies (re-running the seed removes all three).

---

## 6. Known gaps (Module 06 backlog)

- **UI**: the Teaching & learning page covers US-06000–06004, 06008, 06010, 06011, 06013 and 06015 for staff; set-up, LMS content, quizzes, analytics, alerts, archive and the parent app have no screens yet.
- **Schedulers**: overdue homework alerts (US-06016) and morning auto-assign (US-06003) run on request; nothing schedules them.
- **Delivery**: alerts are stored for the parent app (`channel PARENT_APP`); there is no push, SMS or WhatsApp.
- **Content**: no streaming or transcoding for videos, and no SCORM runtime player (packages are validated and stored only). Files live on the local disk (`LMS_ASSET_PATH`).
- **Question generation** is rule-based. A real AI service could replace `McqGenerator` behind the same endpoint.
- **Timetable generator** is greedy. A tightly constrained school may get unplaced periods that a solver would fit; they are reported, not hidden. There is no room allocation in the generator (labs are booked separately).
- **Missing operations**: no delete or close for quizzes and homework, no attendance for remedial classes, no absence withdrawal.
- **Testing**: the Postman collection was validated (JSON, variables) but not run with Newman.
- **Roles to confirm**: the BRD actors are rotated, so the business should confirm the role choices in §2.
