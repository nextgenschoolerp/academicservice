package com.kvs.academic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kvs.academic.entity.HomeworkAssignment;
import com.kvs.academic.repo.HomeworkAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module 06 homework and learner insights: publishing and submitting homework (US-06010), digital evaluation (US-06008),
 * overdue alerts (US-06016), skill analytics (US-06009), leaderboard and badges (US-06017) and remedial classes (US-06012).
 */
class HomeworkInsightsControllerIntegrationTest extends AcademicTestSupport {

    @Autowired private HomeworkAssignmentRepository homeworkRepository;

    private String sst;
    private String neha;
    private String teacher;

    @BeforeEach
    void setUp() throws Exception {
        sst = subject("SST", "Social Science");
        neha = "t-neha-" + run;
        teacher = token("SUBJECT_TEACHER", neha);
        workload(neha, "Neha Kapoor", 30, 6, "SST");
    }

    private JsonNode homework() throws Exception {
        return data(postJson("/homework", teacher, Map.of("sectionId", sectionA.getSectionId(), "subjectId", sst, "title", "Map of Mughal India",
                "instructions", "Mark Agra, Delhi and Fatehpur Sikri", "dueAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(), "maxMarks", 10))
                .andExpect(status().isCreated()));
    }

    // ------------------------------------------------------------------ US-06010 + US-06008

    @Test
    @DisplayName("US-06010/06008: publish, parent submission, roster, evaluation with annotations; marks over max 400; other teacher 403; no resubmit after evaluation 409")
    void homeworkFlow() throws Exception {
        JsonNode hw = homework();
        assertThat(hw.path("studentsInSection").asInt()).isEqualTo(3);
        assertThat(hw.path("missing").asInt()).isEqualTo(3);
        String hwId = hw.path("homeworkId").asText();
        postJson("/homework", teacher, Map.of("sectionId", sectionA.getSectionId(), "subjectId", sst, "title", "Past", "dueAt",
                Instant.now().minusSeconds(60).toString(), "maxMarks", 10)).andExpect(status().isBadRequest());
        postJson("/homework", parentToken(), Map.of("sectionId", sectionA.getSectionId(), "subjectId", sst, "title", "Parent", "dueAt",
                Instant.now().plusSeconds(3600).toString(), "maxMarks", 10)).andExpect(status().isForbidden());

        JsonNode submission = data(postJson("/homework/" + hwId + "/submissions", parentToken(), Map.of("studentId", asha.getStudentId(),
                "submissionText", "Map attached", "attachmentUrl", "https://files.school.test/asha-map.jpg")).andExpect(status().isCreated()));
        assertThat(submission.path("late").asBoolean()).isFalse();
        postJson("/homework/" + hwId + "/submissions", parentToken(), Map.of("studentId", bilal.getStudentId(), "submissionText", "x"))
                .andExpect(status().isForbidden());
        postJson("/homework/" + hwId + "/submissions", parentToken(), Map.of("studentId", asha.getStudentId())).andExpect(status().isBadRequest());
        postJson("/homework/" + hwId + "/submissions", teacher, Map.of("studentId", dev.getStudentId(), "submissionText", "x"))
                .andExpect(status().isBadRequest());

        JsonNode dashboard = data(getAs("/homework/students/" + asha.getStudentId(), parentToken()).andExpect(status().isOk()));
        assertThat(dashboard.path("items").get(0).path("state").asText()).isEqualTo("SUBMITTED");
        assertThat(dashboard.path("className").asText()).isEqualTo("Class 7-A");
        getAs("/homework/students/" + bilal.getStudentId(), parentToken()).andExpect(status().isForbidden());

        JsonNode roster = data(getAs("/homework/" + hwId + "/submissions", teacher).andExpect(status().isOk()));
        assertThat(roster.path("rows").get(0).path("state").asText()).isEqualTo("SUBMITTED");
        assertThat(roster.path("rows").get(1).path("state").asText()).isEqualTo("MISSING");

        String evalPath = "/homework/submissions/" + submission.path("submissionId").asText() + "/evaluation";
        putJson(evalPath, teacher, Map.of("marksAwarded", 11)).andExpect(status().isBadRequest());
        putJson(evalPath, token("SUBJECT_TEACHER", "t-other-" + run), Map.of("marksAwarded", 5)).andExpect(status().isForbidden());
        JsonNode evaluated = data(putJson(evalPath, teacher, Map.of("marksAwarded", 8.5, "feedback", "Neat map; label the rivers",
                "annotations", List.of(Map.of("anchor", "Agra", "comment", "Correct"), Map.of("anchor", "Legend", "comment", "Missing"))))
                .andExpect(status().isOk()));
        assertThat(evaluated.path("status").asText()).isEqualTo("EVALUATED");
        assertThat(evaluated.path("annotations").size()).isEqualTo(2);
        assertThat(evaluated.path("marksAwarded").asDouble()).isEqualTo(8.5);
        var again = postJson("/homework/" + hwId + "/submissions", parentToken(), Map.of("studentId", asha.getStudentId(), "submissionText", "v2"))
                .andExpect(status().isConflict());
        assertThat(errorCode(again)).isEqualTo("ALREADY_EVALUATED");
        putJson(evalPath, token("CLASS_TEACHER"), Map.of("marksAwarded", 9)).andExpect(status().isOk());
        assertThat(data(getAs("/homework/students/" + asha.getStudentId(), parentToken())).path("items").get(0).path("marksAwarded").asDouble())
                .isEqualTo(9.0);
        putJson("/homework/submissions/" + UUID.randomUUID() + "/evaluation", teacher, Map.of("marksAwarded", 1)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ US-06016

    @Test
    @DisplayName("US-06016: overdue alerts reach each student who has not submitted, once; a late submission is marked late")
    void overdueAlerts() throws Exception {
        String hwId = homework().path("homeworkId").asText();
        postJson("/homework/" + hwId + "/submissions", parentToken(), Map.of("studentId", asha.getStudentId(), "submissionText", "Done"))
                .andExpect(status().isCreated());
        HomeworkAssignment stored = homeworkRepository.findById(UUID.fromString(hwId)).orElseThrow();
        stored.setDueAt(Instant.now().minus(1, ChronoUnit.DAYS));
        homeworkRepository.save(stored);

        JsonNode run1 = data(postJson("/homework/overdue-alerts/run", token("ACADEMIC_COORDINATOR"), Map.of()).andExpect(status().isOk()));
        assertThat(run1.path("assignmentsChecked").asInt()).isEqualTo(1);
        assertThat(run1.path("alertsCreated").asInt()).isEqualTo(2);
        assertThat(run1.path("alerts").get(0).path("message").asText()).contains("Map of Mughal India").contains("has not been submitted");
        JsonNode run2 = data(postJson("/homework/overdue-alerts/run", token("CLASS_TEACHER"), Map.of("sectionId", sectionA.getSectionId())));
        assertThat(run2.path("alertsCreated").asInt()).isZero();
        assertThat(run2.path("alreadyAlerted").asInt()).isEqualTo(2);

        assertThat(data(getAs("/homework/overdue-alerts", token("CLASS_TEACHER"), "studentId", bilal.getStudentId().toString())).size()).isEqualTo(1);
        assertThat(data(getAs("/homework/overdue-alerts", parentToken(), "studentId", asha.getStudentId().toString())).size()).isZero();
        JsonNode late = data(postJson("/homework/" + hwId + "/submissions", teacher, Map.of("studentId", chitra.getStudentId(), "submissionText", "Late")));
        assertThat(late.path("late").asBoolean()).isTrue();
        assertThat(data(getAs("/homework/students/" + asha.getStudentId(), parentToken())).path("overdue").asInt()).isZero();
        postJson("/homework/overdue-alerts/run", parentToken(), Map.of()).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ US-06009 / 06017 / 06012 evidence

    /** Asha gets both quiz questions right and 8.5/10 homework; Bilal gets both wrong; Chitra has no evidence. */
    private String evidence() throws Exception {
        String quizId = data(postJson("/quizzes", teacher, Map.of("gradeId", grade.getGradeId(), "subjectId", sst, "quizTitle", "Mughals"))).path("quizId").asText();
        postJson("/quizzes/" + quizId + "/questions", teacher, Map.of("questionText", "Who built the Taj Mahal?", "options",
                List.of("Shah Jahan", "Akbar", "Babur"), "correctOptionIndex", 0, "topic", "Rulers", "marks", 2)).andExpect(status().isCreated());
        postJson("/quizzes/" + quizId + "/questions", teacher, Map.of("questionText", "First Battle of Panipat?", "options",
                List.of("1526", "1556", "1761"), "correctOptionIndex", 0, "topic", "Chronology")).andExpect(status().isCreated());
        putJson("/quizzes/" + quizId + "/settings", teacher, Map.of("durationMinutes", 10, "shuffleQuestions", false, "shuffleOptions", false,
                "maxTabSwitches", 2, "attemptsAllowed", 1)).andExpect(status().isOk());
        postJson("/quizzes/" + quizId + "/publish", teacher, Map.of()).andExpect(status().isOk());
        for (var s : List.of(asha, bilal)) {
            JsonNode attempt = data(postJson("/quizzes/" + quizId + "/attempts", teacher, Map.of("studentId", s.getStudentId())));
            int choice = s == asha ? 0 : 1;
            List<Map<String, Object>> answers = new ArrayList<>();
            attempt.path("questions").forEach(q -> answers.add(Map.of("questionId", q.path("questionId").asText(), "selectedOption", choice)));
            postJson("/attempts/" + attempt.path("attemptId").asText() + "/submit", teacher, Map.of("answers", answers)).andExpect(status().isOk());
        }
        String hwId = homework().path("homeworkId").asText();
        String submissionId = data(postJson("/homework/" + hwId + "/submissions", parentToken(), Map.of("studentId", asha.getStudentId(),
                "submissionText", "Done"))).path("submissionId").asText();
        putJson("/homework/submissions/" + submissionId + "/evaluation", teacher, Map.of("marksAwarded", 8.5)).andExpect(status().isOk());
        return quizId;
    }

    @Test
    @DisplayName("US-06009: topic mastery radar and subject average per student; section averages; parent sees only own child")
    void skillAnalytics() throws Exception {
        evidence();
        JsonNode ashaSkills = data(getAs("/analytics/students/" + asha.getStudentId() + "/skills", parentToken()).andExpect(status().isOk()));
        assertThat(ashaSkills.path("topics").size()).isEqualTo(2);
        assertThat(ashaSkills.path("topics").get(0).path("masteryPercent").asDouble()).isEqualTo(100.0);
        assertThat(ashaSkills.path("subjects").get(0).path("masteryPercent").asDouble()).isEqualTo(92.5);
        assertThat(ashaSkills.path("level").asText()).isEqualTo("MASTERED");
        assertThat(ashaSkills.path("strengths").toString()).contains("Rulers");

        JsonNode bilalSkills = data(getAs("/analytics/students/" + bilal.getStudentId() + "/skills", token("CLASS_TEACHER")));
        assertThat(bilalSkills.path("level").asText()).isEqualTo("NEEDS_SUPPORT");
        assertThat(bilalSkills.path("focusAreas").size()).isEqualTo(2);
        assertThat(data(getAs("/analytics/students/" + chitra.getStudentId() + "/skills", token("CLASS_TEACHER"))).path("level").asText())
                .isEqualTo("NO_EVIDENCE");
        getAs("/analytics/students/" + bilal.getStudentId() + "/skills", parentToken()).andExpect(status().isForbidden());

        JsonNode section = data(getAs("/analytics/sections/" + sectionA.getSectionId() + "/skills", token("TIMETABLE_INCHARGE")).andExpect(status().isOk()));
        assertThat(section.path("students").asInt()).isEqualTo(3);
        for (JsonNode t : section.path("topics")) {
            assertThat(t.path("masteryPercent").asDouble()).isEqualTo(50.0);
            assertThat(t.path("evidenceCount").asInt()).isEqualTo(2);
        }
        getAs("/analytics/sections/" + sectionA.getSectionId() + "/skills", parentToken()).andExpect(status().isForbidden());
        getAs("/analytics/students/" + UUID.randomUUID() + "/skills", token("CLASS_TEACHER")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US-06017: leaderboard points and shared ranks; badges for the top N once; manual badge; duplicate 409; parent 403 to award")
    void leaderboardAndBadges() throws Exception {
        evidence();
        JsonNode board = data(getAs("/leaderboards/sections/" + sectionA.getSectionId(), teacher, "subjectId", sst).andExpect(status().isOk()));
        JsonNode top = board.path("rows").get(0);
        assertThat(top.path("studentName").asText()).isEqualTo("Asha Test");
        assertThat(top.path("points").asDouble()).isEqualTo(142.5);
        assertThat(board.path("rows").get(1).path("rank").asInt()).isEqualTo(2);
        assertThat(board.path("rows").get(2).path("rank").asInt()).isEqualTo(2);

        JsonNode award = data(postJson("/badges/award-top", teacher, Map.of("sectionId", sectionA.getSectionId(), "subjectId", sst, "topN", 1,
                "badgeCode", "HISTORY_STAR", "badgeName", "History Star", "points", 20)).andExpect(status().isOk()));
        assertThat(award.path("awarded").size()).isEqualTo(1);
        assertThat(award.path("awarded").get(0).path("reason").asText()).contains("Rank 1");
        JsonNode repeat = data(postJson("/badges/award-top", teacher, Map.of("sectionId", sectionA.getSectionId(), "subjectId", sst, "topN", 1,
                "badgeCode", "HISTORY_STAR", "badgeName", "History Star", "points", 20)));
        assertThat(repeat.path("awarded").size()).isZero();
        assertThat(repeat.path("alreadyHeld").asInt()).isEqualTo(1);
        assertThat(data(getAs("/leaderboards/sections/" + sectionA.getSectionId(), teacher, "subjectId", sst)).path("rows").get(0)
                .path("points").asDouble()).isEqualTo(162.5);

        postJson("/badges", token("CLASS_TEACHER"), Map.of("studentId", bilal.getStudentId(), "badgeCode", "MOST_IMPROVED",
                "badgeName", "Most Improved", "points", 5, "reason", "Re-test effort")).andExpect(status().isCreated());
        var duplicate = postJson("/badges", token("CLASS_TEACHER"), Map.of("studentId", bilal.getStudentId(), "badgeCode", "MOST_IMPROVED",
                "badgeName", "Most Improved", "reason", "Again")).andExpect(status().isConflict());
        assertThat(errorCode(duplicate)).isEqualTo("BADGE_ALREADY_AWARDED");
        postJson("/badges", token("CLASS_TEACHER"), Map.of("studentId", bilal.getStudentId(), "badgeCode", "bad code", "badgeName", "x", "reason", "x"))
                .andExpect(status().isBadRequest());
        postJson("/badges", parentToken(), Map.of("studentId", asha.getStudentId(), "badgeCode", "SELF_AWARD", "badgeName", "Me", "reason", "x"))
                .andExpect(status().isForbidden());
        assertThat(data(getAs("/badges/students/" + asha.getStudentId(), parentToken()).andExpect(status().isOk())).size()).isEqualTo(1);
        getAs("/leaderboards/sections/" + sectionA.getSectionId(), parentToken()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US-06012: candidates below the threshold; remedial class clashing with the teacher's period 409; after school 201; others' sessions 403")
    void remedialClasses() throws Exception {
        evidence();
        JsonNode candidates = data(getAs("/remedial/candidates", teacher, "sectionId", sectionA.getSectionId().toString(), "subjectId", sst)
                .andExpect(status().isOk()));
        assertThat(candidates.path("candidates").size()).isEqualTo(1);
        assertThat(candidates.path("candidates").get(0).path("studentName").asText()).isEqualTo("Bilal Test");
        assertThat(candidates.path("candidates").get(0).path("averagePercent").asDouble()).isZero();
        assertThat(candidates.path("candidates").get(0).path("weakestTopic").asText()).isIn("Rulers", "Chronology");

        String schedule = bellSchedule();
        allocate(sectionA, sst, neha, 5);
        postJson("/timetables/generate", token("TIMETABLE_INCHARGE"), Map.of("bellScheduleId", schedule, "sectionIds", List.of(sectionA.getSectionId())))
                .andExpect(status().isOk());
        JsonNode monday = null;
        for (JsonNode e : data(getAs("/timetables/teachers/" + neha, teacher)).path("entries")) {
            if (e.path("dayOfWeek").asInt() == 1) {
                monday = e;
            }
        }
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        var busy = postJson("/remedial-sessions", teacher, Map.of("subjectId", sst, "gradeId", grade.getGradeId(), "sessionDate", nextMonday.toString(),
                "startTime", monday.path("startTime").asText(), "endTime", monday.path("endTime").asText(), "focusTopic", "Mughal rulers",
                "studentIds", List.of(bilal.getStudentId()))).andExpect(status().isConflict());
        assertThat(errorCode(busy)).isEqualTo("TEACHER_BUSY");
        JsonNode created = data(postJson("/remedial-sessions", teacher, Map.of("subjectId", sst, "gradeId", grade.getGradeId(),
                "sessionDate", nextMonday.toString(), "startTime", "13:30", "endTime", "14:15", "venue", "Room 12", "focusTopic", "Mughal rulers",
                "studentIds", List.of(bilal.getStudentId(), chitra.getStudentId()))).andExpect(status().isCreated()));
        assertThat(created.path("students").size()).isEqualTo(2);
        assertThat(created.path("teacherName").asText()).isEqualTo("Neha Kapoor");

        Map<String, Object> other = new HashMap<>(Map.of("subjectId", sst, "gradeId", grade.getGradeId(), "teacherId", "t-ravi-" + run,
                "sessionDate", nextMonday.toString(), "startTime", "14:30", "endTime", "15:00", "focusTopic", "Dates", "studentIds", List.of(bilal.getStudentId())));
        postJson("/remedial-sessions", teacher, other).andExpect(status().isForbidden());
        postJson("/remedial-sessions", token("ACADEMIC_COORDINATOR"), other).andExpect(status().isCreated());
        var otherGrade = gradeRepository.save(com.kvs.academic.entity.Grade.builder().tenantId(tenant).gradeName("Class 8").gradeCode("G8-" + run)
                .sequenceOrder(11).build());
        var eightA = sectionRepository.save(com.kvs.academic.entity.Section.builder().gradeId(otherGrade.getGradeId()).sectionName("A").build());
        var eli = student("Eli", eightA, 1, null);
        postJson("/remedial-sessions", teacher, Map.of("subjectId", sst, "gradeId", grade.getGradeId(), "sessionDate", nextMonday.toString(),
                "startTime", "15:00", "endTime", "15:30", "focusTopic", "x", "studentIds", List.of(eli.getStudentId()))).andExpect(status().isBadRequest());
        postJson("/remedial-sessions", teacher, Map.of("subjectId", sst, "gradeId", grade.getGradeId(), "sessionDate", nextMonday.toString(),
                "startTime", "15:00", "endTime", "14:00", "focusTopic", "x", "studentIds", List.of(bilal.getStudentId()))).andExpect(status().isBadRequest());

        // a remedial class during 7-A's own periods would make students miss class
        String other7A = "t-ravi2-" + run;
        workload(other7A, "Ravi Iyer", 30, 6, "SST");
        var inClass = postJson("/remedial-sessions", token("SUBJECT_TEACHER", other7A), Map.of("subjectId", sst, "gradeId", grade.getGradeId(),
                "sessionDate", nextMonday.toString(), "startTime", monday.path("startTime").asText(), "endTime", monday.path("endTime").asText(),
                "focusTopic", "x", "studentIds", List.of(bilal.getStudentId()))).andExpect(status().isConflict());
        assertThat(errorCode(inClass)).isEqualTo("STUDENTS_IN_CLASS");

        assertThat(data(getAs("/remedial-sessions", token("ACADEMIC_COORDINATOR"), "from", nextMonday.toString(), "to", nextMonday.toString())).size())
                .isEqualTo(2);
        assertThat(data(getAs("/remedial-sessions/students/" + asha.getStudentId(), parentToken()).andExpect(status().isOk())).size()).isZero();
        getAs("/remedial-sessions/students/" + bilal.getStudentId(), parentToken()).andExpect(status().isForbidden());
        getAs("/remedial/candidates", parentToken(), "sectionId", sectionA.getSectionId().toString(), "subjectId", sst).andExpect(status().isForbidden());
    }
}
