# School ERP - Module 06: Academics, Timetable & LMS - API Documentation

{info}
Service `academicservice` · package `com.kvs.academic` · port `8087` (test `18087`) · base URL `http://localhost:8087/academicservice/api/v1/lms`.
Shared PostgreSQL database `authservice`; studentservice's `student_master`, `grades`, `sections` and `academic_sessions` are read only.
{info}

h2. Conventions

* *Base path* `/api/v1/lms`. Not `/api/v1/academic…`: studentservice serves `/api/v1/academic`, and the UI proxy matches path prefixes.
* *Authentication*: `Authorization: Bearer <authservice token>`. The token's `tenantId` claim is the institution; `userId` identifies teachers (workloads, lesson plans, homework, bookings) and parents (`student_master.parent_account_id`).
* *Envelope*: success `{ success, message, data, traceId, timestamp }`; error `{ success: false, status, errorCode, message, path, traceId }`. `X-Trace-Id` is echoed.
* *Errors*: `400 VALIDATION_FAILED` / `MALFORMED_REQUEST` / `BAD_REQUEST`, `401`, `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND` (also for another institution's records), `409` with the story's code.
* *Students* have no login yet: the parent app acts for the child (quiz attempts, homework, watch time); a teacher may run a quiz in a supervised lab or record a paper submission.
* *Roles* below are read from the running service (`GET /api/v1/lms/access-rules`). `SUPER_ADMIN` is allowed everywhere.

h2. Roles (BRD actor → role)

||BRD actor||Role||Account (seed)||
|Academic Coordinator|ACADEMIC_COORDINATOR|coordinator_sunita|
|Timetable In-Charge|TIMETABLE_INCHARGE|timetable_rahul|
|Substitution In-Charge|SUBSTITUTION_INCHARGE|substitution_meenal|
|E-Learning Admin|ELEARNING_ADMIN|elearning_farah|
|Subject Teacher|SUBJECT_TEACHER|subjectteacher_neha, teacher_kavya, teacher_imran, teacher_deepa, teacher_arjun, teacher_sana|
|(Class teacher)|CLASS_TEACHER|classteacher_priya|
|Principal|SCHOOL_PRINCIPAL, VICE_PRINCIPAL|principal_anita, viceprincipal_kiran|
|Parent / Student|APPLICANT_PARENT (own children)|parent_rohit (Aarav, Class 6-A)|

The BRD rotates the eight actors across the 50 scenarios of each story (e.g. a Parent "auto-generating quiz questions"); each endpoint is given to the people who do that work in a school.

{expand:title=US-06000: Configure daily period schedules, recess times, and master bell patterns}
*Actor (BRD scenario 1)*: Academic Coordinator

A branch's bell pattern: assembly, numbered teaching periods, recess and lunch, and the working days. Exactly one schedule per branch is active; the timetable generator uses its teaching periods.

*Business rules*
* Slots are sorted by start time and must not overlap (`400 OVERLAPPING_SLOTS`).
* A teaching period lasts 20-120 minutes; a schedule needs at least one.
* The first schedule of a branch becomes active; `activate: true` (or `POST …/activate`) switches the active one.

||Method||Path||Allowed roles||
|POST|`/api/v1/lms/bell-schedules`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SCHOOL_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/bell-schedules`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/bell-schedules/{bellScheduleId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/bell-schedules/{bellScheduleId}/activate`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SCHOOL_PRINCIPAL, SUPER_ADMIN|

*Request* `POST /bell-schedules` (CreateBellScheduleRequest)
||Field||Type||Constraints||
|tenantId|String|Size(max = 64)|
|branchId|String|NotBlank, Size(max = 64)|
|scheduleName|String|NotBlank, Size(max = 100)|
|workingDays|List<@NotNull @Min(1) @Max(7) Integer>|NotEmpty|
|slots|List<BellSlotRequest>|NotEmpty, Size(max = 20), Valid|
|activate|Boolean|-|
{code:language=json}
{
  "branchId": "DPS-DELHI-NORTH",
  "scheduleName": "Exam week timings",
  "workingDays": [
    1,
    2,
    3,
    4,
    5
  ],
  "slots": [
    {
      "slotType": "ASSEMBLY",
      "label": "Assembly",
      "startTime": "07:50",
      "endTime": "08:10"
    },
    {
      "slotType": "PERIOD",
      "label": "Period 1",
      "startTime": "08:10",
      "endTime": "08:50"
    },
    {
      "slotType": "PERIOD",
      "label": "Period 2",
      "startTime": "08:50",
      "endTime": "09:30"
    },
    {
      "slotType": "RECESS",
      "label": "Recess",
      "startTime": "09:30",
      "endTime": "09:50"
    },
    {
      "slotType": "PERIOD",
      "label": "Period 3",
      "startTime": "09:50",
      "endTime": "10:30"
    }
  ]
}
{code}

*Response* `data` (BellScheduleResponse)
||Field||Type||
|bellScheduleId|UUID|
|tenantId|String|
|branchId|String|
|scheduleName|String|
|workingDays|List<Integer>|
|active|Boolean|
|teachingPeriodsPerDay|Integer|
|teachingPeriodsPerWeek|Integer|
|teachingMinutesPerDay|Long|
|schoolStart|LocalTime|
|schoolEnd|LocalTime|
|slots|List<BellSlotResponse>|

{expand}

{expand:title=US-06001: Assign faculty workload hours based on weekly maximum teaching caps}
*Actor (BRD scenario 1)*: Timetable In-Charge

The subject catalog, each teacher's weekly and daily caps and qualified subjects, and who teaches which subject to which section for how many periods a week.

*Business rules*
* An allocation that would take a teacher past the weekly cap is refused (`409 WORKLOAD_CAP_EXCEEDED`).
* A teacher with qualified subjects can only be allocated those (`409 TEACHER_NOT_QUALIFIED`).
* A cap cannot be lowered below what is already allocated (`409`); daily cap ≤ weekly cap (`400`).
* Allocating the same subject to the same section again replaces the teacher / periods.

||Method||Path||Allowed roles||
|GET|`/api/v1/lms/subjects`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/subjects`|TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/workloads`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|PUT|`/api/v1/lms/workloads`|TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|POST|`/api/v1/lms/allocations`|TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/allocations`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|

*Request* `POST /subjects` (CreateSubjectRequest)
||Field||Type||Constraints||
|tenantId|String|Size(max = 64)|
|subjectCode|String|NotBlank, Pattern(regexp = "[A-Z0-9_-]{2,20}", message = "must be 2-20 upper-case letters, digits, '_' or '-'")|
|subjectName|String|NotBlank, Size(max = 100)|
|subjectType|SubjectType|-|
{code:language=json}
{
  "subjectCode": "ART-{{$randomInt}}",
  "subjectName": "Art"
}
{code}

*Request* `PUT /workloads` (FacultyWorkloadRequest)
||Field||Type||Constraints||
|tenantId|String|Size(max = 64)|
|branchId|String|Size(max = 64)|
|teacherId|String|NotBlank, Size(max = 64)|
|teacherName|String|NotBlank, Size(max = 150)|
|weeklyMaxPeriods|Integer|NotNull, Min(1), Max(60)|
|dailyMaxPeriods|Integer|NotNull, Min(1), Max(12)|
|qualifiedSubjectCodes|List<@NotBlank String>|Size(max = 20)|
{code:language=json}
{
  "teacherId": "5eed0001-0001-4000-8000-000000000044",
  "teacherName": "Sana Qureshi",
  "weeklyMaxPeriods": 30,
  "dailyMaxPeriods": 6,
  "qualifiedSubjectCodes": [
    "MATH",
    "SCI"
  ]
}
{code}

*Request* `POST /allocations` (AllocationRequest)
||Field||Type||Constraints||
|sectionId|UUID|NotNull|
|subjectId|UUID|NotNull|
|teacherId|String|NotBlank, Size(max = 64)|
|periodsPerWeek|Integer|NotNull, Min(1), Max(12)|
{code:language=json}
{
  "sectionId": "{{sectionId}}",
  "subjectId": "{{sstSubjectId}}",
  "teacherId": "{{teacherId}}",
  "periodsPerWeek": 6
}
{code}

*Response* `data` (FacultyWorkloadResponse)
||Field||Type||
|workloadId|UUID|
|teacherId|String|
|teacherName|String|
|weeklyMaxPeriods|Integer|
|dailyMaxPeriods|Integer|
|qualifiedSubjectCodes|List<String>|
|allocatedPeriods|Integer|
|timetabledPeriods|Integer|
|remainingPeriods|Integer|
|utilisationPercent|BigDecimal|
|allocations|List<AllocationResponse>|

{expand}

{expand:title=US-06002: Generate conflict-free class timetables using automated constraint engines}
*Actor (BRD scenario 1)*: Timetable In-Charge (Subject Teacher views)

Places every allocated period of the chosen sections into the active bell schedule's teaching slots, replacing those sections' timetables.

*Business rules*
* Hard constraints: one period per slot for a class; a teacher is never in two places at overlapping times (other sections' timetables count); the teacher's daily and weekly caps hold; a subject appears at most `maxPeriodsPerSubjectPerDay` (default 2) times a day.
* Subjects spread over the week (days with fewest periods of the subject first).
* Periods that cannot be placed are listed with the reason, never forced.
* Upcoming proxy substitutions of the replaced periods are cleared (`substitutionsCleared`); run auto-assign again for those days.
* A period can be moved by hand; moves that clash are refused (`409 SECTION_SLOT_TAKEN`, `TEACHER_DOUBLE_BOOKED`, `DAILY_CAP_EXCEEDED`, `TEACHER_NOT_QUALIFIED`).

||Method||Path||Allowed roles||
|POST|`/api/v1/lms/timetables/generate`|TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/timetables/sections/{sectionId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/timetables/teachers/{teacherId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|PUT|`/api/v1/lms/timetables/entries/{timetableId}`|TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/timetables/students/{studentId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|

*Request* `POST /timetables/generate` (GenerateTimetableRequest)
||Field||Type||Constraints||
|bellScheduleId|UUID|NotNull|
|sectionIds|List<@NotNull UUID>|NotEmpty, Size(max = 60)|
|maxPeriodsPerSubjectPerDay|Integer|Min(1), Max(4)|
{code:language=json}
{
  "bellScheduleId": "{{bellScheduleId}}",
  "sectionIds": [
    "{{sectionId}}",
    "{{sectionCId}}"
  ]
}
{code}

*Request* `PUT /timetables/entries/{{timetableId}}` (MoveTimetableEntryRequest)
||Field||Type||Constraints||
|dayOfWeek|Integer|NotNull, Min(1), Max(7)|
|periodNumber|Integer|NotNull, Min(1), Max(20)|
|teacherId|String|Size(max = 64)|
{code:language=json}
{
  "dayOfWeek": 7,
  "periodNumber": 1
}
{code}

*Response* `data` (TimetableGenerationResponse)
||Field||Type||
|bellScheduleId|UUID|
|sectionsScheduled|Integer|
|periodsRequired|Integer|
|periodsPlaced|Integer|
|conflictFree|Boolean|
|sections|List<SectionGenerationResult>|
|substitutionsCleared|Integer|

{expand}

{expand:title=US-06003: Assign proxy substitute teachers automatically during morning roll call}
*Actor (BRD scenario 1)*: Substitution In-Charge

At morning roll call absent teachers are recorded; the optimiser then gives each of their periods that day to a free teacher.

*Business rules*
* A proxy must be present, free at that time (not teaching and not already covering) and under their daily cap.
* Preference order: qualified for the subject (or teaches it), already teaches the class, fewest proxies in the last 7 days, lightest day, name.
* Periods nobody can take are `UNCOVERED`; rerunning keeps existing assignments and only fills the gaps.
* A substitution can be handed to another teacher (`409 PROXY_NOT_AVAILABLE` when they are not free) or cancelled.

||Method||Path||Allowed roles||
|POST|`/api/v1/lms/substitutions/absences`|SUBSTITUTION_INCHARGE, TIMETABLE_INCHARGE, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/substitutions/auto-assign`|SUBSTITUTION_INCHARGE, TIMETABLE_INCHARGE, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/substitutions`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|PUT|`/api/v1/lms/substitutions/{proxyId}`|SUBSTITUTION_INCHARGE, TIMETABLE_INCHARGE, VICE_PRINCIPAL, SUPER_ADMIN|

*Request* `POST /substitutions/absences` (ReportAbsenceRequest)
||Field||Type||Constraints||
|teacherId|String|NotBlank, Size(max = 64)|
|absenceDate|LocalDate|NotNull|
|reason|String|Size(max = 255)|
{code:language=json}
{
  "teacherId": "{{teacherId}}",
  "absenceDate": "{{nextMonday}}",
  "reason": "Medical leave"
}
{code}

*Request* `POST /substitutions/auto-assign` (AutoAssignRequest)
||Field||Type||Constraints||
|date|LocalDate|NotNull|
{code:language=json}
{
  "date": "{{nextMonday}}"
}
{code}

*Request* `PUT /substitutions/{{proxyId}}` (ReassignProxyRequest)
||Field||Type||Constraints||
|proxyTeacherId|String|Size(max = 64)|
|cancel|Boolean|-|
{code:language=json}
{
  "proxyTeacherId": "5eed0001-0001-4000-8000-000000000044"
}
{code}

*Response* `data` (SubstitutionDayResponse)
||Field||Type||
|date|LocalDate|
|dayName|String|
|absentTeachers|Integer|
|periodsToCover|Integer|
|assigned|Integer|
|uncovered|Integer|
|substitutions|List<SubstitutionResponse>|

{expand}

{expand:title=US-06004: Track syllabus completion percentages against annual academic calendars}
*Actor (BRD scenario 1)*: Academic Coordinator (Student / Parent view)

The annual plan lists each class's chapters with target dates. Teachers mark chapters taught per section; coverage compares the share taught with the share due by the date.

*Business rules*
* Status: `BEHIND` when more than 10 points behind the plan, `AHEAD` when more than 10 ahead, else `ON_TRACK`; untaught chapters past their date are `overdue`.
* Chapters must be in teaching order and inside the academic year (`400`).
* A plan cannot be replaced once a chapter is taught (`409 PLAN_IN_USE`) or after the year is archived (`409 ARCHIVED`).
* A parent sees their own child's section only.

||Method||Path||Allowed roles||
|GET|`/api/v1/lms/syllabus/plans`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|PUT|`/api/v1/lms/syllabus/plans`|ACADEMIC_COORDINATOR, SUPER_ADMIN|
|POST|`/api/v1/lms/syllabus/topics/{topicId}/coverage`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/syllabus/coverage`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/syllabus/students/{studentId}/coverage`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|

*Request* `PUT /syllabus/plans` (SyllabusPlanRequest)
||Field||Type||Constraints||
|tenantId|String|Size(max = 64)|
|academicYearId|UUID|-|
|gradeId|UUID|NotNull|
|subjectId|UUID|NotNull|
|topics|List<SyllabusTopicRequest>|NotEmpty, Size(max = 60), Valid|
{code:language=json}
{
  "gradeId": "{{gradeId}}",
  "subjectId": "{{sstSubjectId}}",
  "topics": [
    {
      "chapterTitle": "Only chapter",
      "plannedCompletionDate": "2026-12-01"
    }
  ]
}
{code}

*Request* `POST /syllabus/topics/{{topicId}}/coverage` (CoverageRequest)
||Field||Type||Constraints||
|sectionId|UUID|NotNull|
|completedOn|LocalDate|NotNull|
|notes|String|Size(max = 500)|
{code:language=json}
{
  "sectionId": "{{sectionId}}",
  "completedOn": "{{today}}",
  "notes": "Map activity"
}
{code}

*Response* `data` (SyllabusCoverageResponse)
||Field||Type||
|sectionId|UUID|
|className|String|
|asOf|LocalDate|
|subjects|List<SubjectCoverage>|

{expand}

{expand:title=US-06005: Upload digital learning assets, lecture videos, and reference e-books}
*Actor (BRD scenario 1)*: E-Learning Admin

PDF notes, MP4 lecture videos, EPUB e-books and SCORM packages are uploaded per class, subject and chapter; external lectures are added as links.

*Business rules*
* Accepted types: `application/pdf`, `video/mp4`, `application/epub+zip`, `application/zip` (SCORM); the file's leading bytes must match (`400 CONTENT_MISMATCH`), other types `400 UNSUPPORTED_FILE_TYPE`.
* A SCORM zip must contain `imsmanifest.xml` (`400 INVALID_SCORM_PACKAGE`); a video needs `durationSeconds`.
* Files are stored under generated names with a SHA-256 checksum; links must be `https://`.
* A parent lists assets with `studentId` and sees only their child's class.

||Method||Path||Allowed roles||
|POST|`/api/v1/lms/assets/links`|ELEARNING_ADMIN, SUBJECT_TEACHER, SUPER_ADMIN|
|POST|`/api/v1/lms/assets`|ELEARNING_ADMIN, SUBJECT_TEACHER, SUPER_ADMIN|
|GET|`/api/v1/lms/assets`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|

*Request* `POST /assets/links` (CreateAssetLinkRequest)
||Field||Type||Constraints||
|title|String|NotBlank, Size(max = 200)|
|gradeId|UUID|NotNull|
|subjectId|UUID|NotNull|
|chapter|String|Size(max = 200)|
|assetType|AssetType|-|
|url|String|NotBlank, Size(max = 500), Pattern(regexp = "https://.+", message = "must be an https:// link")|
|durationSeconds|Integer|Min(1), Max(36000)|
{code:language=json}
{
  "title": "Mughal architecture",
  "gradeId": "{{gradeId}}",
  "subjectId": "{{sstSubjectId}}",
  "chapter": "The Mughal Empire",
  "assetType": "VIDEO",
  "url": "https://videos.dps.edu.in/sst/mughal-architecture",
  "durationSeconds": 600
}
{code}

*Response* `data` (LearningAssetResponse)
||Field||Type||
|assetId|UUID|
|title|String|
|assetType|AssetType|
|gradeId|UUID|
|subjectId|UUID|
|subjectCode|String|
|chapter|String|
|fileName|String|
|contentType|String|
|sizeBytes|Long|
|checksumSha256|String|
|externalUrl|String|
|durationSeconds|Integer|
|contentUrl|String|
|uploadedBy|String|
|uploadedAt|Instant|

{expand}

{expand:title=US-06006: Auto-generate multiple-choice quiz questions from uploaded chapter text}
*Actor (BRD scenario 1)*: Subject Teacher / E-Learning Admin

Draft quizzes get fill-in-the-blank MCQs generated from chapter text by the built-in generator (no external AI service): each usable sentence loses one key term (a name, a number or a long content word) and the distractors are other key terms of the same kind; years get nearby years.

*Business rules*
* Text of 200-20,000 characters; 1-25 questions per call; output is deterministic for the same text.
* Text without recurring key terms gives `400 NOT_ENOUGH_KEY_TERMS`.
* Questions can be added, edited or removed only while the quiz is a draft (`409 QUIZ_NOT_EDITABLE`); a correct option must exist and options must differ (`400`).

||Method||Path||Allowed roles||
|POST|`/api/v1/lms/quizzes`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|POST|`/api/v1/lms/quizzes/{quizId}/questions/generate`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|POST|`/api/v1/lms/quizzes/{quizId}/questions`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|

*Request* `POST /quizzes` (CreateQuizRequest)
||Field||Type||Constraints||
|tenantId|String|Size(max = 64)|
|gradeId|UUID|NotNull|
|sectionId|UUID|-|
|subjectId|UUID|NotNull|
|quizTitle|String|NotBlank, Size(max = 200)|
{code:language=json}
{
  "gradeId": "{{gradeId}}",
  "subjectId": "{{sstSubjectId}}",
  "quizTitle": "The Mughals - quick check"
}
{code}

*Request* `POST /quizzes/{{quizId}}/questions/generate` (GenerateQuestionsRequest)
||Field||Type||Constraints||
|chapterText|String|NotBlank, Size(min = 200, max = 20000)|
|count|Integer|NotNull, Min(1), Max(25)|
|topic|String|Size(max = 200)|
{code:language=json}
{
  "chapterText": "The Mughal Empire was founded by Babur in 1526 after the First Battle of Panipat. Humayun lost the throne to Sher Shah Suri in 1540 and regained it fifteen years later. Akbar expanded the empire across northern India and introduced the mansabdari administration. Akbar moved his court to Fatehpur Sikri, a city he built near Agra. Shah Jahan built the Taj Mahal at Agra in memory of Mumtaz Mahal. Aurangzeb extended Mughal rule into the Deccan but faced resistance from Shivaji and the Marathas.",
  "count": 5,
  "topic": "Mughal Empire"
}
{code}

*Request* `POST /quizzes/{{quizId}}/questions` (QuestionRequest)
||Field||Type||Constraints||
|questionText|String|NotBlank, Size(max = 1000)|
|options|List<@NotBlank @Size(max = 300) String>|NotNull, Size(min = 2, max = 6)|
|correctOptionIndex|Integer|NotNull, Min(0)|
|topic|String|Size(max = 200)|
|marks|Integer|Min(1), Max(10)|
|explanation|String|Size(max = 1000)|
{code:language=json}
{
  "questionText": "Who built the Taj Mahal?",
  "options": [
    "Shah Jahan",
    "Akbar",
    "Babur",
    "Humayun"
  ],
  "correctOptionIndex": 0,
  "topic": "Rulers",
  "marks": 2
}
{code}

*Response* `data` (QuestionGenerationResponse)
||Field||Type||
|requested|Integer|
|generated|Integer|
|sentencesScanned|Integer|
|questions|List<QuestionResponse>|

{expand}

{expand:title=US-06007: Conduct proctored online assessments with anti-cheat tab-switch detection}
*Actor (BRD scenario 1)*: Principal (report); Parent app (attempt)

Students take the quiz in the parent app (students have no login yet; a teacher can run it in a supervised lab). Each attempt fixes its own question and option order and never shows the answer key.

*Business rules*
* `TAB_SWITCH` and `WINDOW_BLUR` events count against the limit; going over it submits the attempt and flags it (`AUTO_SUBMITTED`). `COPY_PASTE` flags without submitting.
* An attempt not submitted by its deadline (+30 s grace) closes as `TIMED_OUT` with its saved answers.
* Starting again returns the running attempt; after the allowed attempts `409 ATTEMPTS_EXHAUSTED`; the quiz must be published and open (`409`).
* A parent may only start or open attempts of their own children (`403`).

||Method||Path||Allowed roles||
|GET|`/api/v1/lms/quizzes`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/quizzes/{quizId}/attempts`|APPLICANT_PARENT, SUBJECT_TEACHER, SUPER_ADMIN|
|PUT|`/api/v1/lms/attempts/{attemptId}/answers`|APPLICANT_PARENT, SUBJECT_TEACHER, SUPER_ADMIN|
|POST|`/api/v1/lms/attempts/{attemptId}/events`|APPLICANT_PARENT, SUBJECT_TEACHER, SUPER_ADMIN|
|POST|`/api/v1/lms/attempts/{attemptId}/submit`|APPLICANT_PARENT, SUBJECT_TEACHER, SUPER_ADMIN|
|GET|`/api/v1/lms/quizzes/{quizId}/proctoring-report`|SCHOOL_PRINCIPAL, VICE_PRINCIPAL, ACADEMIC_COORDINATOR, SUBJECT_TEACHER, ELEARNING_ADMIN, SUPER_ADMIN|

*Request* `POST /quizzes/{{quizId}}/attempts` (StartAttemptRequest)
||Field||Type||Constraints||
|studentId|UUID|NotNull|
{code:language=json}
{
  "studentId": "{{studentId}}"
}
{code}

*Request* `PUT /attempts/{{attemptId}}/answers` (AnswersRequest)
||Field||Type||Constraints||
|answers|List<AnswerItem>|NotNull, Size(max = 200), Valid|
{code:language=json}
{
  "answers": [
    {
      "questionId": "{{attemptQuestionId}}",
      "selectedOption": 0
    }
  ]
}
{code}

*Request* `POST /attempts/{{attemptId}}/events` (ProctorEventRequest)
||Field||Type||Constraints||
|eventType|ProctorEventType|NotNull|
|detail|String|Size(max = 255)|
{code:language=json}
{
  "eventType": "TAB_SWITCH",
  "detail": "visibilitychange"
}
{code}

*Response* `data` (AttemptResponse)
||Field||Type||
|attemptId|UUID|
|quizId|UUID|
|quizTitle|String|
|studentId|UUID|
|status|AttemptStatus|
|startedAt|Instant|
|deadlineAt|Instant|
|remainingSeconds|Long|
|tabSwitchCount|Integer|
|maxTabSwitches|Integer|
|flagged|Boolean|
|flagReason|String|
|score|Integer|
|maxScore|Integer|
|questions|List<AttemptQuestion>|

{expand}

{expand:title=US-06008: Evaluate submitted homework assignments digitally with annotations and marks}
*Actor (BRD scenario 1)*: Subject Teacher

The teacher sees who submitted, who is late and who is missing, and marks each submission with feedback and annotations.

*Business rules*
* Marks cannot exceed the homework's maximum (`400`).
* Only the teacher who set the homework evaluates it (class teacher and coordinator may too); other teachers get `403`.

||Method||Path||Allowed roles||
|GET|`/api/v1/lms/homework/{homeworkId}/submissions`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|PUT|`/api/v1/lms/homework/submissions/{submissionId}/evaluation`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|POST|`/api/v1/lms/homework/{homeworkId}/submissions`|APPLICANT_PARENT, SUBJECT_TEACHER, CLASS_TEACHER, SUPER_ADMIN|

*Request* `PUT /homework/submissions/{{submissionId}}/evaluation` (EvaluateHomeworkRequest)
||Field||Type||Constraints||
|marksAwarded|BigDecimal|NotNull, DecimalMin("0")|
|feedback|String|Size(max = 2000)|
|annotations|List<Annotation>|Size(max = 50), Valid|
{code:language=json}
{
  "marksAwarded": 8.5,
  "feedback": "Neat map; label the rivers",
  "annotations": [
    {
      "anchor": "Agra",
      "comment": "Correct"
    },
    {
      "anchor": "Legend",
      "comment": "Missing"
    }
  ]
}
{code}

*Response* `data` (HomeworkSubmissionResponse)
||Field||Type||
|submissionId|UUID|
|homeworkId|UUID|
|studentId|UUID|
|studentName|String|
|rollNumber|Integer|
|submittedAt|Instant|
|late|Boolean|
|status|SubmissionStatus|
|submissionText|String|
|attachmentUrl|String|
|marksAwarded|BigDecimal|
|maxMarks|Integer|
|feedback|String|
|annotations|List<Annotation>|
|evaluatedBy|String|
|evaluatedAt|Instant|

{expand}

{expand:title=US-06009: View student learning skill analytics and topic mastery radar charts}
*Actor (BRD scenario 1)*: Subject Teacher / Class Teacher (Parent view)

Topic mastery from quiz answers (one radar axis per question topic) and subject averages from quizzes and evaluated homework.

*Business rules*
* Levels: `MASTERED` from 80%, `DEVELOPING` from 50%, `NEEDS_SUPPORT` below; `NO_EVIDENCE` without data.
* Strengths: up to three topics at 80%+; focus areas: up to three below 50%.
* Section view averages each student's topic mastery; a parent sees only their own child.

||Method||Path||Allowed roles||
|GET|`/api/v1/lms/analytics/students/{studentId}/skills`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|GET|`/api/v1/lms/analytics/sections/{sectionId}/skills`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|

*Response* `data` (SkillAnalyticsResponse)
||Field||Type||
|studentId|UUID|
|studentName|String|
|className|String|
|overallMasteryPercent|BigDecimal|
|level|String|
|topics|List<SkillAxis>|
|subjects|List<SkillAxis>|
|strengths|List<String>|
|focusAreas|List<String>|

{expand}

{expand:title=US-06010: Publish daily homework assignments directly to parent app dashboards}
*Actor (BRD scenario 1)*: Subject Teacher

Homework is published to a section and appears at once on each child's dashboard in the parent app; the parent hands it in there.

*Business rules*
* The due time must be in the future (`400`); an attached asset must be for the same class.
* A submission after the due time is accepted and marked late; it can be replaced until evaluated (`409 ALREADY_EVALUATED`).
* A submission needs text or an attachment link (`400`); the student must be in the homework's section.

||Method||Path||Allowed roles||
|POST|`/api/v1/lms/homework`|SUBJECT_TEACHER, CLASS_TEACHER, SUPER_ADMIN|
|POST|`/api/v1/lms/homework/{homeworkId}/submissions`|APPLICANT_PARENT, SUBJECT_TEACHER, CLASS_TEACHER, SUPER_ADMIN|
|GET|`/api/v1/lms/homework/students/{studentId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|GET|`/api/v1/lms/homework`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|

*Request* `POST /homework` (CreateHomeworkRequest)
||Field||Type||Constraints||
|sectionId|UUID|NotNull|
|subjectId|UUID|NotNull|
|title|String|NotBlank, Size(max = 200)|
|instructions|String|Size(max = 4000)|
|dueAt|Instant|NotNull, Future|
|maxMarks|Integer|NotNull, Min(1), Max(100)|
|assetId|UUID|-|
{code:language=json}
{
  "sectionId": "{{sectionId}}",
  "subjectId": "{{sstSubjectId}}",
  "title": "Map of Mughal India",
  "instructions": "Mark Agra, Delhi and Fatehpur Sikri",
  "dueAt": "{{dueAt}}",
  "maxMarks": 10
}
{code}

*Request* `POST /homework/{{homeworkId}}/submissions` (SubmitHomeworkRequest)
||Field||Type||Constraints||
|studentId|UUID|NotNull|
|submissionText|String|Size(max = 4000)|
|attachmentUrl|String|Size(max = 500)|
{code:language=json}
{
  "studentId": "{{studentId}}",
  "submissionText": "Map attached",
  "attachmentUrl": "https://files.dps.edu.in/aarav-map.jpg"
}
{code}

*Response* `data` (HomeworkResponse)
||Field||Type||
|homeworkId|UUID|
|title|String|
|instructions|String|
|sectionId|UUID|
|className|String|
|subjectId|UUID|
|subjectCode|String|
|subjectName|String|
|teacherId|String|
|teacherName|String|
|assignedOn|LocalDate|
|dueAt|Instant|
|maxMarks|Integer|
|assetId|UUID|
|studentsInSection|Integer|
|submitted|Integer|
|evaluated|Integer|
|missing|Integer|

{expand}

{expand:title=US-06011: Manage computer lab and science laboratory room reservation schedules}
*Actor (BRD scenario 1)*: Subject Teacher (books); Academic Coordinator (rooms)

Teachers book lab slots for a class; coordinators register the rooms.

*Business rules*
* A room holds one booking at a time (`409 LAB_ALREADY_BOOKED`); a teacher cannot hold overlapping bookings (`409 TEACHER_DOUBLE_BOOKED`).
* A section larger than the room's capacity cannot book it (`409 CAPACITY_EXCEEDED`); no past dates; end after start (`400`).
* Only the booking teacher (or coordinator / timetable in-charge) cancels (`403`).

||Method||Path||Allowed roles||
|GET|`/api/v1/lms/labs`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/labs/{roomId}/reservations`|SUBJECT_TEACHER, CLASS_TEACHER, SUBSTITUTION_INCHARGE, TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|POST|`/api/v1/lms/labs/reservations/{reservationId}/cancel`|SUBJECT_TEACHER, CLASS_TEACHER, SUBSTITUTION_INCHARGE, TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|POST|`/api/v1/lms/labs`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUPER_ADMIN|

*Request* `POST /labs/{{roomId}}/reservations` (LabReservationRequest)
||Field||Type||Constraints||
|reservationDate|LocalDate|NotNull|
|startTime|LocalTime|NotNull|
|endTime|LocalTime|NotNull|
|sectionId|UUID|-|
|purpose|String|NotBlank, Size(max = 255)|
{code:language=json}
{
  "reservationDate": "{{tomorrow}}",
  "startTime": "10:30",
  "endTime": "11:10",
  "sectionId": "{{sectionId}}",
  "purpose": "Spreadsheet practical"
}
{code}

*Request* `POST /labs/reservations/{{reservationId}}/cancel` (CancelReservationRequest)
||Field||Type||Constraints||
|reason|String|NotBlank, Size(max = 255)|
{code:language=json}
{
  "reason": "Class trip"
}
{code}

*Request* `POST /labs` (LabRoomRequest)
||Field||Type||Constraints||
|tenantId|String|Size(max = 64)|
|branchId|String|NotBlank, Size(max = 64)|
|roomName|String|NotBlank, Size(max = 100)|
|roomType|RoomType|NotNull|
|capacity|Integer|NotNull, Min(1), Max(200)|
{code:language=json}
{
  "branchId": "DPS-DELHI-NORTH",
  "roomName": "Language Lab",
  "roomType": "LANGUAGE_LAB",
  "capacity": 30
}
{code}

*Response* `data` (LabReservationResponse)
||Field||Type||
|reservationId|UUID|
|roomId|UUID|
|roomName|String|
|reservationDate|LocalDate|
|startTime|LocalTime|
|endTime|LocalTime|
|sectionId|UUID|
|className|String|
|teacherId|String|
|teacherName|String|
|purpose|String|
|status|ReservationStatus|
|cancelReason|String|

{expand}

{expand:title=US-06012: Schedule remedial class sessions for students needing academic support}
*Actor (BRD scenario 1)*: Subject Teacher

Students below a threshold (default 50%) in a subject are suggested; the teacher schedules a remedial class for them.

*Business rules*
* The class must not clash with the teacher's timetable or other remedial classes (`409 TEACHER_BUSY`) nor with the students' regular periods (`409 STUDENTS_IN_CLASS`).
* Students must be in the given class (`400`); only a coordinator schedules for another teacher (`403`).

||Method||Path||Allowed roles||
|GET|`/api/v1/lms/remedial/candidates`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|POST|`/api/v1/lms/remedial-sessions`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/remedial-sessions/students/{studentId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|

*Request* `POST /remedial-sessions` (RemedialSessionRequest)
||Field||Type||Constraints||
|subjectId|UUID|NotNull|
|gradeId|UUID|NotNull|
|teacherId|String|Size(max = 64)|
|sessionDate|LocalDate|NotNull, FutureOrPresent|
|startTime|LocalTime|NotNull|
|endTime|LocalTime|NotNull|
|venue|String|Size(max = 100)|
|focusTopic|String|NotBlank, Size(max = 200)|
|studentIds|List<@NotNull UUID>|NotEmpty, Size(max = 40)|
{code:language=json}
{
  "subjectId": "{{sstSubjectId}}",
  "gradeId": "{{gradeId}}",
  "sessionDate": "{{nextMonday}}",
  "startTime": "14:30",
  "endTime": "15:15",
  "venue": "Room 12",
  "focusTopic": "Mughal rulers",
  "studentIds": [
    "{{studentId}}"
  ]
}
{code}

*Response* `data` (RemedialSessionResponse)
||Field||Type||
|remedialSessionId|UUID|
|subjectId|UUID|
|subjectCode|String|
|gradeId|UUID|
|teacherId|String|
|teacherName|String|
|sessionDate|LocalDate|
|startTime|LocalTime|
|endTime|LocalTime|
|venue|String|
|focusTopic|String|
|status|RemedialStatus|
|students|List<RemedialStudent>|

{expand}

{expand:title=US-06013: Track teacher daily lesson plan submissions and principal approval statuses}
*Actor (BRD scenario 1)*: Subject Teacher (writes); Principal (reviews)

Teachers write daily lesson plans and submit them; the principal, vice principal or coordinator approves or returns them; a compliance report shows missing days.

*Business rules*
* Flow: `DRAFT` → `SUBMITTED` → `APPROVED` or `RETURNED` (remarks required) → `SUBMITTED` …; only DRAFT / RETURNED plans change (`409`).
* Teachers write and see only their own plans; nobody reviews their own plan (`403`).
* Compliance: days in the range on which the teacher has timetabled periods, minus days absent; missing days have no submitted plan (range up to 92 days).

||Method||Path||Allowed roles||
|POST|`/api/v1/lms/lesson-plans`|SUBJECT_TEACHER, CLASS_TEACHER, SUPER_ADMIN|
|POST|`/api/v1/lms/lesson-plans/{lessonPlanId}/review`|SCHOOL_PRINCIPAL, VICE_PRINCIPAL, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/lesson-plans`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/lesson-plans/compliance`|SCHOOL_PRINCIPAL, VICE_PRINCIPAL, ACADEMIC_COORDINATOR, SUPER_ADMIN|

*Request* `POST /lesson-plans` (LessonPlanRequest)
||Field||Type||Constraints||
|sectionId|UUID|NotNull|
|subjectId|UUID|NotNull|
|planDate|LocalDate|NotNull|
|topicId|UUID|-|
|objectives|String|NotBlank, Size(max = 2000)|
|activities|String|Size(max = 4000)|
|assessment|String|Size(max = 2000)|
|submit|Boolean|-|
{code:language=json}
{
  "sectionId": "{{sectionId}}",
  "subjectId": "{{sstSubjectId}}",
  "planDate": "{{today}}",
  "objectives": "Locate the Mughal capitals on a map",
  "activities": "Map work in pairs",
  "submit": true
}
{code}

*Request* `POST /lesson-plans/{{lessonPlanId}}/review` (ReviewLessonPlanRequest)
||Field||Type||Constraints||
|decision|ReviewDecision|NotNull|
|remarks|String|Size(max = 1000)|
{code:language=json}
{
  "decision": "RETURNED"
}
{code}

*Response* `data` (LessonPlanResponse)
||Field||Type||
|lessonPlanId|UUID|
|academicYearId|UUID|
|teacherId|String|
|teacherName|String|
|sectionId|UUID|
|className|String|
|subjectId|UUID|
|subjectCode|String|
|planDate|LocalDate|
|topicId|UUID|
|chapterTitle|String|
|objectives|String|
|activities|String|
|assessment|String|
|status|LessonPlanStatus|
|submittedAt|Instant|
|reviewedBy|String|
|reviewedAt|Instant|
|reviewRemarks|String|
|archived|Boolean|

{expand}

{expand:title=US-06014: Configure quiz countdown timers and randomized question order rules}
*Actor (BRD scenario 1)*: Subject Teacher

Duration, question and option shuffling, the tab-switch limit, attempts allowed and the availability window of a quiz.

*Business rules*
* Duration 1-180 min, tab switches 0-10, attempts 1-5; the window must be longer than the duration (`400`).
* Rules cannot change once a student has started (`409 QUIZ_IN_USE`).
* Publishing needs at least one question (`409 QUIZ_HAS_NO_QUESTIONS`).

||Method||Path||Allowed roles||
|PUT|`/api/v1/lms/quizzes/{quizId}/settings`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|POST|`/api/v1/lms/quizzes/{quizId}/publish`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|POST|`/api/v1/lms/quizzes/{quizId}/questions`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|

*Request* `PUT /quizzes/{{quizId}}/settings` (QuizSettingsRequest)
||Field||Type||Constraints||
|durationMinutes|Integer|NotNull, Min(1), Max(180)|
|shuffleQuestions|Boolean|NotNull|
|shuffleOptions|Boolean|NotNull|
|maxTabSwitches|Integer|NotNull, Min(0), Max(10)|
|attemptsAllowed|Integer|NotNull, Min(1), Max(5)|
|availableFrom|Instant|-|
|availableUntil|Instant|-|
{code:language=json}
{
  "durationMinutes": 15,
  "shuffleQuestions": true,
  "shuffleOptions": true,
  "maxTabSwitches": 2,
  "attemptsAllowed": 1
}
{code}

*Response* `data` (QuizResponse)
||Field||Type||
|quizId|UUID|
|quizTitle|String|
|gradeId|UUID|
|sectionId|UUID|
|subjectId|UUID|
|subjectCode|String|
|teacherId|String|
|status|QuizStatus|
|durationMinutes|Integer|
|shuffleQuestions|Boolean|
|shuffleOptions|Boolean|
|maxTabSwitches|Integer|
|attemptsAllowed|Integer|
|availableFrom|Instant|
|availableUntil|Instant|
|publishedAt|Instant|
|questionCount|Integer|
|totalMarks|Integer|
|questions|List<QuestionResponse>|

{expand}

{expand:title=US-06015: Export master faculty timetables to PDF for staff room noticeboards}
*Actor (BRD scenario 1)*: Principal

One landscape A4 page per teacher: bell slots down the side (breaks shaded), working days across, class and subject in each cell.

*Business rules*
* Every teacher in the workload register gets a page, or one teacher with `teacherId`.
* The export is audited (`FACULTY_TIMETABLE_EXPORTED`).

||Method||Path||Allowed roles||
|GET|`/api/v1/lms/timetables/faculty/pdf`|SCHOOL_PRINCIPAL, VICE_PRINCIPAL, TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUBSTITUTION_INCHARGE, SUPER_ADMIN|

{expand}

{expand:title=US-06016: Send automated alerts to students for overdue homework submissions}
*Actor (BRD scenario 1)*: Academic Coordinator

For homework past its due time, every student who has not submitted gets one alert in the parent app.

*Business rules*
* One alert per student per homework, however often the run is repeated (`alreadyAlerted`).
* The run covers the institution, or one section with `sectionId`; there is no scheduler yet (run it from the screen or a job).

||Method||Path||Allowed roles||
|POST|`/api/v1/lms/homework/overdue-alerts/run`|ACADEMIC_COORDINATOR, CLASS_TEACHER, SUBJECT_TEACHER, SUPER_ADMIN|
|GET|`/api/v1/lms/homework/overdue-alerts`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|

*Request* `POST /homework/overdue-alerts/run` (OverdueAlertRunRequest)
||Field||Type||Constraints||
|sectionId|UUID|-|
{code:language=json}
{}
{code}

*Response* `data` (OverdueAlertRunResponse)
||Field||Type||
|assignmentsChecked|Integer|
|alertsCreated|Integer|
|alreadyAlerted|Integer|
|alerts|List<HomeworkAlertResponse>|

{expand}

{expand:title=US-06017: Award digital skill badges to top-performing students on LMS leaderboards}
*Actor (BRD scenario 1)*: Subject Teacher / E-Learning Admin

A section leaderboard (optionally per subject) and badges for the top performers.

*Business rules*
* Points: best attempt of each quiz = its score % (0-100), evaluated homework = half its % (0-50), completed video = 10, plus badge points.
* Equal points share a rank (1, 2, 2, 4); `award-top` gives the badge to everyone ranked within the top N who has points and does not hold it yet.
* A student holds a badge code once per subject (`409 BADGE_ALREADY_AWARDED`); parents cannot award (`403`).

||Method||Path||Allowed roles||
|GET|`/api/v1/lms/leaderboards/sections/{sectionId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/badges/award-top`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|POST|`/api/v1/lms/badges`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|GET|`/api/v1/lms/badges/students/{studentId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|

*Request* `POST /badges/award-top` (AwardTopBadgesRequest)
||Field||Type||Constraints||
|sectionId|UUID|NotNull|
|subjectId|UUID|-|
|topN|Integer|NotNull, Min(1), Max(10)|
|badgeCode|String|NotBlank, Pattern(regexp = "[A-Z0-9_]{3,50}", message = "must be 3-50 upper-case letters, digits or '_'")|
|badgeName|String|NotBlank, Size(max = 100)|
|points|Integer|Min(0), Max(100)|
{code:language=json}
{
  "sectionId": "{{sectionId}}",
  "subjectId": "{{sstSubjectId}}",
  "topN": 3,
  "badgeCode": "HISTORY_STAR",
  "badgeName": "History Star",
  "points": 20
}
{code}

*Request* `POST /badges` (AwardBadgeRequest)
||Field||Type||Constraints||
|studentId|UUID|NotNull|
|subjectId|UUID|-|
|badgeCode|String|NotBlank, Pattern(regexp = "[A-Z0-9_]{3,50}", message = "must be 3-50 upper-case letters, digits or '_'")|
|badgeName|String|NotBlank, Size(max = 100)|
|points|Integer|Min(0), Max(100)|
|reason|String|NotBlank, Size(max = 255)|
{code:language=json}
{
  "studentId": "{{studentId}}",
  "badgeCode": "bad code",
  "badgeName": "x",
  "reason": "x"
}
{code}

*Response* `data` (LeaderboardResponse)
||Field||Type||
|sectionId|UUID|
|className|String|
|subjectId|UUID|
|subjectCode|String|
|rows|List<LeaderboardRow>|

{expand}

{expand:title=US-06018: Monitor student video lecture watch-time durations and completion rates}
*Actor (BRD scenario 1)*: Subject Teacher (report); Parent app (progress)

The parent app reports playback progress; teachers see watch time and completion per section.

*Business rules*
* Completion counts once the furthest point reached is 90% of the video; only continuous playback moves it (a jump beyond what was watched is ignored).
* Only lecture videos with a duration are tracked (`400`); the student must be in the video's class.
* Progress is recorded by the parent app only (`403` for staff).

||Method||Path||Allowed roles||
|POST|`/api/v1/lms/assets/{assetId}/watch-progress`|APPLICANT_PARENT, SUPER_ADMIN|
|GET|`/api/v1/lms/assets/{assetId}/watch-report`|SUBJECT_TEACHER, CLASS_TEACHER, ELEARNING_ADMIN, ACADEMIC_COORDINATOR, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|

*Request* `POST /assets/{{videoAssetId}}/watch-progress` (WatchProgressRequest)
||Field||Type||Constraints||
|studentId|UUID|NotNull|
|positionSeconds|Integer|NotNull, Min(0)|
|watchedSeconds|Integer|Min(0), Max(3600)|
{code:language=json}
{
  "studentId": "{{studentId}}",
  "positionSeconds": 300,
  "watchedSeconds": 300
}
{code}

*Response* `data` (WatchReportResponse)
||Field||Type||
|assetId|UUID|
|title|String|
|durationSeconds|Integer|
|sectionId|UUID|
|className|String|
|students|Integer|
|started|Integer|
|completed|Integer|
|completionRatePercent|BigDecimal|
|averageCompletionPercent|BigDecimal|
|rows|List<WatchReportRow>|

{expand}

{expand:title=US-06019: Archive historical lesson plans and syllabus repos for faculty reference}
*Actor (BRD scenario 1)*: Academic Coordinator / Principal

After a year ends its lesson plans and syllabus plans are archived: read-only, but searchable for faculty reference.

*Business rules*
* Only a year whose end date has passed can be archived (`409 YEAR_NOT_CLOSED`); repeating the archive changes nothing.
* Archived plans and chapters cannot change (`409 ARCHIVED`).
* Search lesson plans by year, class, subject and text (objectives, activities, assessment, chapter).

||Method||Path||Allowed roles||
|POST|`/api/v1/lms/archives`|ACADEMIC_COORDINATOR, SCHOOL_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/archives/lesson-plans`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/archives/syllabus`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|

*Request* `POST /archives` (ArchiveYearRequest)
||Field||Type||Constraints||
|tenantId|String|Size(max = 64)|
|academicYearId|UUID|NotNull|
{code:language=json}
{
  "academicYearId": "{{academicYearId}}"
}
{code}

*Response* `data` (ArchiveResultResponse)
||Field||Type||
|academicYearId|UUID|
|sessionName|String|
|lessonPlansArchived|Integer|
|syllabusTopicsArchived|Integer|
|archivedAt|Instant|

{expand}

h2. Endpoint index

||Method||Path||Allowed roles||
|GET|`/api/v1/lms/access-rules`|any signed-in user|
|GET|`/api/v1/lms/allocations`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/allocations`|TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|DELETE|`/api/v1/lms/allocations/{allocationId}`|TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/analytics/sections/{sectionId}/skills`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/analytics/students/{studentId}/skills`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/archives`|ACADEMIC_COORDINATOR, SCHOOL_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/archives/lesson-plans`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/archives/syllabus`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/assets`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/assets`|ELEARNING_ADMIN, SUBJECT_TEACHER, SUPER_ADMIN|
|POST|`/api/v1/lms/assets/links`|ELEARNING_ADMIN, SUBJECT_TEACHER, SUPER_ADMIN|
|GET|`/api/v1/lms/assets/{assetId}/content`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/assets/{assetId}/watch-progress`|APPLICANT_PARENT, SUPER_ADMIN|
|GET|`/api/v1/lms/assets/{assetId}/watch-report`|SUBJECT_TEACHER, CLASS_TEACHER, ELEARNING_ADMIN, ACADEMIC_COORDINATOR, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/attempts/{attemptId}`|APPLICANT_PARENT, SUBJECT_TEACHER, SUPER_ADMIN|
|PUT|`/api/v1/lms/attempts/{attemptId}/answers`|APPLICANT_PARENT, SUBJECT_TEACHER, SUPER_ADMIN|
|POST|`/api/v1/lms/attempts/{attemptId}/events`|APPLICANT_PARENT, SUBJECT_TEACHER, SUPER_ADMIN|
|POST|`/api/v1/lms/attempts/{attemptId}/submit`|APPLICANT_PARENT, SUBJECT_TEACHER, SUPER_ADMIN|
|POST|`/api/v1/lms/badges`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|POST|`/api/v1/lms/badges/award-top`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|GET|`/api/v1/lms/badges/students/{studentId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|GET|`/api/v1/lms/bell-schedules`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/bell-schedules`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SCHOOL_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/bell-schedules/{bellScheduleId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/bell-schedules/{bellScheduleId}/activate`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SCHOOL_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/homework`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/homework`|SUBJECT_TEACHER, CLASS_TEACHER, SUPER_ADMIN|
|GET|`/api/v1/lms/homework/overdue-alerts`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/homework/overdue-alerts/run`|ACADEMIC_COORDINATOR, CLASS_TEACHER, SUBJECT_TEACHER, SUPER_ADMIN|
|GET|`/api/v1/lms/homework/students/{studentId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|PUT|`/api/v1/lms/homework/submissions/{submissionId}/evaluation`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/homework/{homeworkId}/submissions`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/homework/{homeworkId}/submissions`|APPLICANT_PARENT, SUBJECT_TEACHER, CLASS_TEACHER, SUPER_ADMIN|
|GET|`/api/v1/lms/labs`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/labs`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUPER_ADMIN|
|POST|`/api/v1/lms/labs/reservations/{reservationId}/cancel`|SUBJECT_TEACHER, CLASS_TEACHER, SUBSTITUTION_INCHARGE, TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/labs/{roomId}/reservations`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/labs/{roomId}/reservations`|SUBJECT_TEACHER, CLASS_TEACHER, SUBSTITUTION_INCHARGE, TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/leaderboards/sections/{sectionId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/lesson-plans`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/lesson-plans`|SUBJECT_TEACHER, CLASS_TEACHER, SUPER_ADMIN|
|GET|`/api/v1/lms/lesson-plans/compliance`|SCHOOL_PRINCIPAL, VICE_PRINCIPAL, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|PUT|`/api/v1/lms/lesson-plans/{lessonPlanId}`|SUBJECT_TEACHER, CLASS_TEACHER, SUPER_ADMIN|
|POST|`/api/v1/lms/lesson-plans/{lessonPlanId}/review`|SCHOOL_PRINCIPAL, VICE_PRINCIPAL, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|POST|`/api/v1/lms/lesson-plans/{lessonPlanId}/submit`|SUBJECT_TEACHER, CLASS_TEACHER, SUPER_ADMIN|
|GET|`/api/v1/lms/quizzes`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/quizzes`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|GET|`/api/v1/lms/quizzes/{quizId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/quizzes/{quizId}/attempts`|APPLICANT_PARENT, SUBJECT_TEACHER, SUPER_ADMIN|
|GET|`/api/v1/lms/quizzes/{quizId}/proctoring-report`|SCHOOL_PRINCIPAL, VICE_PRINCIPAL, ACADEMIC_COORDINATOR, SUBJECT_TEACHER, ELEARNING_ADMIN, SUPER_ADMIN|
|POST|`/api/v1/lms/quizzes/{quizId}/publish`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|POST|`/api/v1/lms/quizzes/{quizId}/questions`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|POST|`/api/v1/lms/quizzes/{quizId}/questions/generate`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|DELETE|`/api/v1/lms/quizzes/{quizId}/questions/{questionId}`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|PUT|`/api/v1/lms/quizzes/{quizId}/questions/{questionId}`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|PUT|`/api/v1/lms/quizzes/{quizId}/settings`|SUBJECT_TEACHER, ACADEMIC_COORDINATOR, ELEARNING_ADMIN, SUPER_ADMIN|
|GET|`/api/v1/lms/remedial-sessions`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/remedial-sessions`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/remedial-sessions/students/{studentId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|GET|`/api/v1/lms/remedial/candidates`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/subjects`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/subjects`|TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/substitutions`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/substitutions/absences`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/substitutions/absences`|SUBSTITUTION_INCHARGE, TIMETABLE_INCHARGE, VICE_PRINCIPAL, SUPER_ADMIN|
|POST|`/api/v1/lms/substitutions/auto-assign`|SUBSTITUTION_INCHARGE, TIMETABLE_INCHARGE, VICE_PRINCIPAL, SUPER_ADMIN|
|PUT|`/api/v1/lms/substitutions/{proxyId}`|SUBSTITUTION_INCHARGE, TIMETABLE_INCHARGE, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/syllabus/coverage`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/syllabus/plans`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|PUT|`/api/v1/lms/syllabus/plans`|ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/syllabus/students/{studentId}/coverage`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|POST|`/api/v1/lms/syllabus/topics/{topicId}/coverage`|SUBJECT_TEACHER, CLASS_TEACHER, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|PUT|`/api/v1/lms/timetables/entries/{timetableId}`|TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/timetables/faculty/pdf`|SCHOOL_PRINCIPAL, VICE_PRINCIPAL, TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUBSTITUTION_INCHARGE, SUPER_ADMIN|
|POST|`/api/v1/lms/timetables/generate`|TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
|GET|`/api/v1/lms/timetables/sections/{sectionId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/timetables/students/{studentId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN, APPLICANT_PARENT|
|GET|`/api/v1/lms/timetables/teachers/{teacherId}`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|GET|`/api/v1/lms/workloads`|ACADEMIC_COORDINATOR, TIMETABLE_INCHARGE, SUBSTITUTION_INCHARGE, ELEARNING_ADMIN, SUBJECT_TEACHER, CLASS_TEACHER, SCHOOL_PRINCIPAL, VICE_PRINCIPAL, SUPER_ADMIN|
|PUT|`/api/v1/lms/workloads`|TIMETABLE_INCHARGE, ACADEMIC_COORDINATOR, SUPER_ADMIN|
