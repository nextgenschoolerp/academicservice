/**
 * academicservice entities. Module 06 owns subjects, bell_schedules, bell_periods, faculty_workloads, subject_allocations,
 * master_timetables, faculty_absences, faculty_proxy_substitutions, syllabus_topics, syllabus_coverage, learning_assets,
 * video_watch_progress, lms_quizzes, lms_quiz_questions, lms_quiz_attempts, lms_proctor_events, homework_assignments,
 * homework_submissions, homework_overdue_alerts, skill_badges, lab_rooms, lab_reservations, remedial_sessions and lesson_plans.
 * {@code StudentMaster}, {@code Grade}, {@code Section} and {@code AcademicSession} map studentservice's tables in the shared
 * database as a read model: academicservice only reads them (rosters, class names, academic years) and never writes them.
 */
package com.kvs.academic.entity;
