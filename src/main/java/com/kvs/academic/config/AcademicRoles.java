package com.kvs.academic.config;

/**
 * Who may call the Module 06 academic endpoints (@PreAuthorize expressions). BRD actors → roles:
 * Academic Coordinator → ACADEMIC_COORDINATOR, Timetable In-Charge → TIMETABLE_INCHARGE,
 * Substitution In-Charge → SUBSTITUTION_INCHARGE, E-Learning Admin → ELEARNING_ADMIN, Subject Teacher → SUBJECT_TEACHER,
 * Principal → SCHOOL_PRINCIPAL (and VICE_PRINCIPAL), Parent → APPLICANT_PARENT (own children only).
 * The BRD rotates the eight actors across the 50 scenarios of each story; each rule follows who does the work in a school.
 * Students have no login yet: the parent app acts for the child (quiz attempts, homework, watch time).
 */
public final class AcademicRoles {

    private AcademicRoles() {
    }

    private static final String STAFF = "'ACADEMIC_COORDINATOR', 'TIMETABLE_INCHARGE', 'SUBSTITUTION_INCHARGE', 'ELEARNING_ADMIN', "
            + "'SUBJECT_TEACHER', 'CLASS_TEACHER', 'SCHOOL_PRINCIPAL', 'VICE_PRINCIPAL', 'SUPER_ADMIN'";

    /** Read schedules, subjects, timetables, coverage, assets, reports */
    public static final String STAFF_VIEW = "hasAnyRole(" + STAFF + ")";

    /** Staff views that a parent may also open for their own child (timetable, coverage, assets, homework, analytics) */
    public static final String STAFF_OR_PARENT = "hasAnyRole(" + STAFF + ", 'APPLICANT_PARENT')";

    /** US-06000: bell schedules, periods and recess */
    public static final String BELL_SCHEDULE = "hasAnyRole('ACADEMIC_COORDINATOR', 'TIMETABLE_INCHARGE', 'SCHOOL_PRINCIPAL', 'SUPER_ADMIN')";

    /** US-06001: subject catalog, faculty workload caps and subject-teacher allocations */
    public static final String WORKLOAD = "hasAnyRole('TIMETABLE_INCHARGE', 'ACADEMIC_COORDINATOR', 'SUPER_ADMIN')";

    /** US-06002: generate and adjust class timetables */
    public static final String TIMETABLE = "hasAnyRole('TIMETABLE_INCHARGE', 'ACADEMIC_COORDINATOR', 'SUPER_ADMIN')";

    /** US-06015: master faculty timetable PDF */
    public static final String TIMETABLE_EXPORT = "hasAnyRole('SCHOOL_PRINCIPAL', 'VICE_PRINCIPAL', 'TIMETABLE_INCHARGE', "
            + "'ACADEMIC_COORDINATOR', 'SUBSTITUTION_INCHARGE', 'SUPER_ADMIN')";

    /** US-06003: record absences and assign proxies */
    public static final String SUBSTITUTION = "hasAnyRole('SUBSTITUTION_INCHARGE', 'TIMETABLE_INCHARGE', 'VICE_PRINCIPAL', 'SUPER_ADMIN')";

    /** US-06004: annual syllabus plan (chapters and target dates) */
    public static final String SYLLABUS_PLAN = "hasAnyRole('ACADEMIC_COORDINATOR', 'SUPER_ADMIN')";

    /** US-06004: mark a chapter as covered in a section */
    public static final String SYLLABUS_COVERAGE = "hasAnyRole('SUBJECT_TEACHER', 'CLASS_TEACHER', 'ACADEMIC_COORDINATOR', 'SUPER_ADMIN')";

    /** US-06005: upload e-learning assets */
    public static final String ASSET_UPLOAD = "hasAnyRole('ELEARNING_ADMIN', 'SUBJECT_TEACHER', 'SUPER_ADMIN')";

    /** US-06006, US-06014: author quizzes, generate questions, set timer and shuffle rules */
    public static final String QUIZ_AUTHOR = "hasAnyRole('SUBJECT_TEACHER', 'ACADEMIC_COORDINATOR', 'ELEARNING_ADMIN', 'SUPER_ADMIN')";

    /** US-06007: take a proctored quiz (parent app for their child, or a teacher supervising a lab session) */
    public static final String QUIZ_TAKE = "hasAnyRole('APPLICANT_PARENT', 'SUBJECT_TEACHER', 'SUPER_ADMIN')";

    /** US-06007: proctoring report */
    public static final String PROCTOR_REPORT = "hasAnyRole('SCHOOL_PRINCIPAL', 'VICE_PRINCIPAL', 'ACADEMIC_COORDINATOR', "
            + "'SUBJECT_TEACHER', 'ELEARNING_ADMIN', 'SUPER_ADMIN')";

    /** US-06010: publish homework */
    public static final String HOMEWORK_SET = "hasAnyRole('SUBJECT_TEACHER', 'CLASS_TEACHER', 'SUPER_ADMIN')";

    /** US-06010: hand in homework (parent app, or a teacher recording a paper submission) */
    public static final String HOMEWORK_SUBMIT = "hasAnyRole('APPLICANT_PARENT', 'SUBJECT_TEACHER', 'CLASS_TEACHER', 'SUPER_ADMIN')";

    /** US-06008: evaluate submissions */
    public static final String HOMEWORK_EVALUATE = "hasAnyRole('SUBJECT_TEACHER', 'CLASS_TEACHER', 'ACADEMIC_COORDINATOR', 'SUPER_ADMIN')";

    /** US-06016: run overdue homework alerts */
    public static final String HOMEWORK_ALERTS = "hasAnyRole('ACADEMIC_COORDINATOR', 'CLASS_TEACHER', 'SUBJECT_TEACHER', 'SUPER_ADMIN')";

    /** US-06017: award badges */
    public static final String BADGES = "hasAnyRole('SUBJECT_TEACHER', 'CLASS_TEACHER', 'ACADEMIC_COORDINATOR', 'ELEARNING_ADMIN', 'SUPER_ADMIN')";

    /** US-06018: report watch progress from the parent app */
    public static final String WATCH_TRACK = "hasAnyRole('APPLICANT_PARENT', 'SUPER_ADMIN')";

    /** US-06018: watch-time report */
    public static final String WATCH_REPORT = "hasAnyRole('SUBJECT_TEACHER', 'CLASS_TEACHER', 'ELEARNING_ADMIN', 'ACADEMIC_COORDINATOR', "
            + "'SCHOOL_PRINCIPAL', 'VICE_PRINCIPAL', 'SUPER_ADMIN')";

    /** US-06011: register lab rooms */
    public static final String LAB_ROOMS = "hasAnyRole('ACADEMIC_COORDINATOR', 'TIMETABLE_INCHARGE', 'SUPER_ADMIN')";

    /** US-06011: book and cancel lab slots */
    public static final String LAB_BOOK = "hasAnyRole('SUBJECT_TEACHER', 'CLASS_TEACHER', 'SUBSTITUTION_INCHARGE', 'TIMETABLE_INCHARGE', "
            + "'ACADEMIC_COORDINATOR', 'SUPER_ADMIN')";

    /** US-06012: schedule remedial sessions */
    public static final String REMEDIAL = "hasAnyRole('SUBJECT_TEACHER', 'CLASS_TEACHER', 'ACADEMIC_COORDINATOR', 'SUPER_ADMIN')";

    /** US-06013: write and submit own lesson plans */
    public static final String LESSON_PLAN_WRITE = "hasAnyRole('SUBJECT_TEACHER', 'CLASS_TEACHER', 'SUPER_ADMIN')";

    /** US-06013: approve or return lesson plans, and the submission compliance report */
    public static final String LESSON_PLAN_REVIEW = "hasAnyRole('SCHOOL_PRINCIPAL', 'VICE_PRINCIPAL', 'ACADEMIC_COORDINATOR', 'SUPER_ADMIN')";

    /** US-06019: archive a closed academic year */
    public static final String ARCHIVE = "hasAnyRole('ACADEMIC_COORDINATOR', 'SCHOOL_PRINCIPAL', 'SUPER_ADMIN')";
}
