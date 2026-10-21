package com.kvs.academic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kvs.academic.entity.AcademicSession;
import com.kvs.academic.entity.Grade;
import com.kvs.academic.entity.Section;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module 06 curriculum and facilities: syllabus coverage (US-06004), lab reservations (US-06011), lesson plans and approval
 * (US-06013) and the year-end archive (US-06019).
 */
class CurriculumControllerIntegrationTest extends AcademicTestSupport {

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

    private Map<String, Object> plan(UUID yearId, LocalDate start, String... chapters) {
        List<Map<String, Object>> topics = new ArrayList<>();
        int[] offsets = {30, 60, 120, 200};
        for (int i = 0; i < chapters.length; i++) {
            topics.add(Map.of("chapterTitle", chapters[i], "plannedCompletionDate", start.plusDays(offsets[i]).toString(), "plannedPeriods", 8));
        }
        return Map.of("academicYearId", yearId, "gradeId", grade.getGradeId(), "subjectId", sst, "topics", topics);
    }

    // ------------------------------------------------------------------ US-06004

    @Test
    @DisplayName("US-06004: coverage against the plan (BEHIND / ON_TRACK, overdue chapters); parent sees own child; plan locked once taught 409")
    void syllabusCoverage() throws Exception {
        LocalDate start = year.getStartDate();
        JsonNode saved = data(putJson("/syllabus/plans", token("ACADEMIC_COORDINATOR"),
                plan(year.getAcademicYearId(), start, "The Mughal Empire", "Rulers and Buildings", "Tribes and Nomads", "Devotional Paths"))
                .andExpect(status().isOk()));
        assertThat(saved.path("topics").size()).isEqualTo(4);
        String first = saved.path("topics").get(0).path("topicId").asText();
        String second = saved.path("topics").get(1).path("topicId").asText();
        String asOf = start.plusDays(100).toString();

        postJson("/syllabus/topics/" + first + "/coverage", teacher, Map.of("sectionId", sectionA.getSectionId(), "completedOn", LocalDate.now().toString()))
                .andExpect(status().isOk());
        JsonNode a = data(getAs("/syllabus/coverage", teacher, "sectionId", sectionA.getSectionId().toString(), "asOf", asOf).andExpect(status().isOk()))
                .path("subjects").get(0);
        assertThat(a.path("completionPercent").asDouble()).isEqualTo(25.0);
        assertThat(a.path("expectedPercent").asDouble()).isEqualTo(50.0);
        assertThat(a.path("status").asText()).isEqualTo("BEHIND");
        assertThat(a.path("nextTopic").asText()).isEqualTo("Rulers and Buildings");
        assertThat(a.path("topics").get(1).path("overdue").asBoolean()).isTrue();

        postJson("/syllabus/topics/" + second + "/coverage", teacher, Map.of("sectionId", sectionA.getSectionId(), "completedOn", LocalDate.now().toString()));
        assertThat(data(getAs("/syllabus/coverage", teacher, "sectionId", sectionA.getSectionId().toString(), "asOf", asOf)).path("subjects").get(0)
                .path("status").asText()).isEqualTo("ON_TRACK");
        assertThat(data(getAs("/syllabus/coverage", teacher, "sectionId", sectionB.getSectionId().toString(), "asOf", asOf)).path("subjects").get(0)
                .path("completedTopics").asInt()).isZero();
        assertThat(data(getAs("/syllabus/students/" + asha.getStudentId() + "/coverage", parentToken()).andExpect(status().isOk()))
                .path("className").asText()).isEqualTo("Class 7-A");
        getAs("/syllabus/students/" + dev.getStudentId() + "/coverage", parentToken()).andExpect(status().isForbidden());

        var inUse = putJson("/syllabus/plans", token("ACADEMIC_COORDINATOR"), plan(year.getAcademicYearId(), start, "Only one"))
                .andExpect(status().isConflict());
        assertThat(errorCode(inUse)).isEqualTo("PLAN_IN_USE");
        putJson("/syllabus/plans", token("ACADEMIC_COORDINATOR"), Map.of("gradeId", grade.getGradeId(), "subjectId", subject("HIN", "Hindi"),
                "topics", List.of(Map.of("chapterTitle", "B", "plannedCompletionDate", start.plusDays(60).toString()),
                        Map.of("chapterTitle", "A", "plannedCompletionDate", start.plusDays(30).toString())))).andExpect(status().isBadRequest());
        putJson("/syllabus/plans", token("ACADEMIC_COORDINATOR"), Map.of("gradeId", grade.getGradeId(), "subjectId", sst,
                "topics", List.of(Map.of("chapterTitle", "Next year", "plannedCompletionDate", year.getEndDate().plusDays(5).toString()))))
                .andExpect(status().isBadRequest());
        putJson("/syllabus/plans", teacher, plan(year.getAcademicYearId(), start, "Mine")).andExpect(status().isForbidden());
        postJson("/syllabus/topics/" + first + "/coverage", teacher, Map.of("sectionId", sectionA.getSectionId(),
                "completedOn", LocalDate.now().plusDays(3).toString())).andExpect(status().isBadRequest());
        Grade eight = gradeRepository.save(Grade.builder().tenantId(tenant).gradeName("Class 8").gradeCode("G8-" + run).sequenceOrder(11).build());
        Section eightA = sectionRepository.save(Section.builder().gradeId(eight.getGradeId()).sectionName("A").build());
        postJson("/syllabus/topics/" + first + "/coverage", teacher, Map.of("sectionId", eightA.getSectionId(), "completedOn", LocalDate.now().toString()))
                .andExpect(status().isBadRequest());
        postJson("/syllabus/topics/" + UUID.randomUUID() + "/coverage", teacher, Map.of("sectionId", sectionA.getSectionId(),
                "completedOn", LocalDate.now().toString())).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ US-06011

    @Test
    @DisplayName("US-06011: lab bookings refuse overlaps (409), double-booked teachers (409) and too-large classes (409); only the booker cancels")
    void labReservations() throws Exception {
        String lab = data(postJson("/labs", token("ACADEMIC_COORDINATOR"), Map.of("branchId", "BR-1", "roomName", "Computer Lab 1",
                "roomType", "COMPUTER_LAB", "capacity", 30)).andExpect(status().isCreated())).path("roomId").asText();
        String smallLab = data(postJson("/labs", token("TIMETABLE_INCHARGE"), Map.of("branchId", "BR-1", "roomName", "Chemistry Lab",
                "roomType", "SCIENCE_LAB", "capacity", 2)).andExpect(status().isCreated())).path("roomId").asText();
        postJson("/labs", token("ACADEMIC_COORDINATOR"), Map.of("branchId", "BR-1", "roomName", "computer lab 1", "roomType", "COMPUTER_LAB",
                "capacity", 30)).andExpect(status().isConflict());
        postJson("/labs", teacher, Map.of("branchId", "BR-1", "roomName", "Mine", "roomType", "COMPUTER_LAB", "capacity", 3)).andExpect(status().isForbidden());

        String tomorrow = LocalDate.now().plusDays(1).toString();
        JsonNode booked = data(postJson("/labs/" + lab + "/reservations", teacher, Map.of("reservationDate", tomorrow, "startTime", "10:00",
                "endTime", "11:00", "sectionId", sectionA.getSectionId(), "purpose", "Spreadsheet practical")).andExpect(status().isCreated()));
        assertThat(booked.path("className").asText()).isEqualTo("Class 7-A");
        assertThat(booked.path("teacherName").asText()).isEqualTo("Neha Kapoor");
        String ravi = token("SUBJECT_TEACHER", "t-ravi-" + run);
        var overlap = postJson("/labs/" + lab + "/reservations", ravi, Map.of("reservationDate", tomorrow, "startTime", "10:30", "endTime", "11:30",
                "purpose", "Typing")).andExpect(status().isConflict());
        assertThat(errorCode(overlap)).isEqualTo("LAB_ALREADY_BOOKED");
        String otherLab = data(postJson("/labs", token("ACADEMIC_COORDINATOR"), Map.of("branchId", "BR-1", "roomName", "Physics Lab",
                "roomType", "SCIENCE_LAB", "capacity", 40))).path("roomId").asText();
        var twice = postJson("/labs/" + otherLab + "/reservations", teacher, Map.of("reservationDate", tomorrow, "startTime", "10:15", "endTime", "10:45",
                "purpose", "Optics")).andExpect(status().isConflict());
        assertThat(errorCode(twice)).isEqualTo("TEACHER_DOUBLE_BOOKED");
        var tooMany = postJson("/labs/" + smallLab + "/reservations", teacher, Map.of("reservationDate", tomorrow, "startTime", "12:00",
                "endTime", "13:00", "sectionId", sectionA.getSectionId(), "purpose", "Titration")).andExpect(status().isConflict());
        assertThat(errorCode(tooMany)).isEqualTo("CAPACITY_EXCEEDED");
        postJson("/labs/" + lab + "/reservations", ravi, Map.of("reservationDate", tomorrow, "startTime", "11:00", "endTime", "12:00",
                "purpose", "Back to back")).andExpect(status().isCreated());
        postJson("/labs/" + lab + "/reservations", ravi, Map.of("reservationDate", LocalDate.now().minusDays(1).toString(), "startTime", "09:00",
                "endTime", "10:00", "purpose", "Past")).andExpect(status().isBadRequest());
        postJson("/labs/" + lab + "/reservations", ravi, Map.of("reservationDate", tomorrow, "startTime", "15:00", "endTime", "14:00",
                "purpose", "Backwards")).andExpect(status().isBadRequest());
        postJson("/labs/" + lab + "/reservations", parentToken(), Map.of("reservationDate", tomorrow, "startTime", "15:00", "endTime", "16:00",
                "purpose", "Parent")).andExpect(status().isForbidden());

        String cancelPath = "/labs/reservations/" + booked.path("reservationId").asText() + "/cancel";
        postJson(cancelPath, ravi, Map.of("reason", "Not mine")).andExpect(status().isForbidden());
        assertThat(data(postJson(cancelPath, teacher, Map.of("reason", "Class trip")).andExpect(status().isOk())).path("status").asText())
                .isEqualTo("CANCELLED");
        postJson(cancelPath, teacher, Map.of("reason", "Again")).andExpect(status().isConflict());
        postJson("/labs/" + lab + "/reservations", ravi, Map.of("reservationDate", tomorrow, "startTime", "10:00", "endTime", "11:00",
                "purpose", "Freed slot")).andExpect(status().isCreated());
        assertThat(data(getAs("/labs/" + lab + "/reservations", teacher, "from", tomorrow, "to", tomorrow)).size()).isEqualTo(3);
        postJson("/labs/" + UUID.randomUUID() + "/reservations", teacher, Map.of("reservationDate", tomorrow, "startTime", "10:00",
                "endTime", "11:00", "purpose", "x")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ US-06013

    @Test
    @DisplayName("US-06013: draft → submitted → returned (remarks required) → resubmitted → approved; own plans only; no self-review")
    void lessonPlanApproval() throws Exception {
        String today = LocalDate.now().toString();
        Map<String, Object> body = new HashMap<>(Map.of("sectionId", sectionA.getSectionId(), "subjectId", sst, "planDate", today,
                "objectives", "Locate the Mughal capitals on a map", "activities", "Map work in pairs"));
        JsonNode draft = data(postJson("/lesson-plans", teacher, body).andExpect(status().isCreated()));
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        postJson("/lesson-plans", teacher, body).andExpect(status().isConflict());
        String path = "/lesson-plans/" + draft.path("lessonPlanId").asText();

        assertThat(data(postJson(path + "/submit", teacher, Map.of())).path("status").asText()).isEqualTo("SUBMITTED");
        putJson(path, teacher, body).andExpect(status().isConflict());
        postJson(path + "/review", token("SCHOOL_PRINCIPAL"), Map.of("decision", "RETURNED")).andExpect(status().isBadRequest());
        JsonNode returned = data(postJson(path + "/review", token("SCHOOL_PRINCIPAL"), Map.of("decision", "RETURNED",
                "remarks", "Add an exit ticket")).andExpect(status().isOk()));
        assertThat(returned.path("status").asText()).isEqualTo("RETURNED");
        putJson(path, token("SUBJECT_TEACHER", "t-ravi-" + run), body).andExpect(status().isForbidden());
        body.put("assessment", "Exit ticket: name two capitals");
        body.put("submit", true);
        assertThat(data(putJson(path, teacher, body).andExpect(status().isOk())).path("status").asText()).isEqualTo("SUBMITTED");
        postJson(path + "/review", teacher, Map.of("decision", "APPROVED")).andExpect(status().isForbidden());
        JsonNode approved = data(postJson(path + "/review", token("VICE_PRINCIPAL"), Map.of("decision", "APPROVED")).andExpect(status().isOk()));
        assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.path("reviewedBy").asText()).startsWith("vice_principal");
        postJson(path + "/review", token("VICE_PRINCIPAL"), Map.of("decision", "APPROVED")).andExpect(status().isConflict());

        // a super admin who wrote a plan cannot approve it
        String own = data(postJson("/lesson-plans", token("SUPER_ADMIN"), Map.of("sectionId", sectionB.getSectionId(), "subjectId", sst,
                "planDate", today, "objectives", "Demo", "submit", true))).path("lessonPlanId").asText();
        postJson("/lesson-plans/" + own + "/review", token("SUPER_ADMIN"), Map.of("decision", "APPROVED")).andExpect(status().isForbidden());

        postJson("/lesson-plans", token("SUBJECT_TEACHER", "t-ravi-" + run), Map.of("sectionId", sectionB.getSectionId(), "subjectId", sst,
                "planDate", today, "objectives", "Ravi's plan")).andExpect(status().isCreated());
        assertThat(data(getAs("/lesson-plans", teacher)).size()).isEqualTo(1);
        assertThat(data(getAs("/lesson-plans", token("SCHOOL_PRINCIPAL"))).size()).isEqualTo(3);
        assertThat(data(getAs("/lesson-plans", token("SCHOOL_PRINCIPAL"), "status", "APPROVED")).size()).isEqualTo(1);
        postJson("/lesson-plans", teacher, Map.of("sectionId", sectionA.getSectionId(), "subjectId", sst, "planDate", today)).andExpect(status().isBadRequest());
        postJson("/lesson-plans", token("ACADEMIC_COORDINATOR"), body).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US-06013: compliance counts timetabled days, skips absences, and shows missing days per teacher")
    void lessonPlanCompliance() throws Exception {
        String schedule = bellSchedule();
        allocate(sectionA, sst, neha, 5);
        postJson("/timetables/generate", token("TIMETABLE_INCHARGE"), Map.of("bellScheduleId", schedule, "sectionIds", List.of(sectionA.getSectionId())))
                .andExpect(status().isOk());
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        for (int d = 0; d < 2; d++) {
            postJson("/lesson-plans", teacher, Map.of("sectionId", sectionA.getSectionId(), "subjectId", sst, "planDate", monday.plusDays(d).toString(),
                    "objectives", "Day " + d, "submit", true)).andExpect(status().isCreated());
        }
        postJson("/substitutions/absences", token("SUBSTITUTION_INCHARGE"), Map.of("teacherId", neha, "absenceDate", monday.plusDays(2).toString()))
                .andExpect(status().isCreated());
        JsonNode report = data(getAs("/lesson-plans/compliance", token("SCHOOL_PRINCIPAL"), "from", monday.toString(),
                "to", monday.plusDays(4).toString()).andExpect(status().isOk()));
        JsonNode row = report.path("teachers").get(0);
        assertThat(row.path("teacherName").asText()).isEqualTo("Neha Kapoor");
        assertThat(row.path("teachingDays").asInt()).isEqualTo(4);
        assertThat(row.path("plansSubmitted").asInt()).isEqualTo(2);
        assertThat(row.path("pendingReview").asInt()).isEqualTo(2);
        assertThat(row.path("missingDays").asInt()).isEqualTo(2);
        assertThat(row.path("compliancePercent").asDouble()).isEqualTo(50.0);
        getAs("/lesson-plans/compliance", token("SCHOOL_PRINCIPAL"), "from", monday.toString(), "to", monday.minusDays(1).toString())
                .andExpect(status().isBadRequest());
        getAs("/lesson-plans/compliance", teacher, "from", monday.toString(), "to", monday.toString()).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ US-06019

    @Test
    @DisplayName("US-06019: only a closed year can be archived (409); archive is read-only (409) but searchable; rerun archives nothing new")
    void archive() throws Exception {
        LocalDate lastStart = year.getStartDate().minusYears(1);
        AcademicSession last = sessionRepository.save(AcademicSession.builder().tenantId(tenant)
                .sessionName(lastStart.getYear() + "-" + (lastStart.getYear() + 1)).startDate(lastStart).endDate(year.getStartDate().minusDays(1))
                .isActive(false).build());
        JsonNode lastPlan = data(putJson("/syllabus/plans", token("ACADEMIC_COORDINATOR"),
                plan(last.getAcademicYearId(), lastStart, "Medieval India", "The Delhi Sultans")).andExpect(status().isOk()));
        String plan = data(postJson("/lesson-plans", teacher, Map.of("sectionId", sectionA.getSectionId(), "subjectId", sst,
                "planDate", lastStart.plusDays(40).toString(), "objectives", "Compare the Khalji and Tughlaq markets",
                "topicId", lastPlan.path("topics").get(1).path("topicId").asText())).andExpect(status().isCreated())).path("lessonPlanId").asText();

        var open = postJson("/archives", token("ACADEMIC_COORDINATOR"), Map.of("academicYearId", year.getAcademicYearId())).andExpect(status().isConflict());
        assertThat(errorCode(open)).isEqualTo("YEAR_NOT_CLOSED");
        postJson("/archives", teacher, Map.of("academicYearId", last.getAcademicYearId())).andExpect(status().isForbidden());
        JsonNode result = data(postJson("/archives", token("SCHOOL_PRINCIPAL"), Map.of("academicYearId", last.getAcademicYearId()))
                .andExpect(status().isOk()));
        assertThat(result.path("lessonPlansArchived").asInt()).isEqualTo(1);
        assertThat(result.path("syllabusTopicsArchived").asInt()).isEqualTo(2);
        assertThat(data(postJson("/archives", token("SCHOOL_PRINCIPAL"), Map.of("academicYearId", last.getAcademicYearId())))
                .path("lessonPlansArchived").asInt()).isZero();

        var readOnly = postJson("/lesson-plans/" + plan + "/submit", teacher, Map.of()).andExpect(status().isConflict());
        assertThat(errorCode(readOnly)).isEqualTo("ARCHIVED");
        postJson("/syllabus/topics/" + lastPlan.path("topics").get(0).path("topicId").asText() + "/coverage", teacher,
                Map.of("sectionId", sectionA.getSectionId(), "completedOn", LocalDate.now().toString())).andExpect(status().isConflict());
        putJson("/syllabus/plans", token("ACADEMIC_COORDINATOR"), plan(last.getAcademicYearId(), lastStart, "Rewrite")).andExpect(status().isConflict());

        JsonNode found = data(getAs("/archives/lesson-plans", teacher, "q", "tughlaq").andExpect(status().isOk()));
        assertThat(found.size()).isEqualTo(1);
        assertThat(found.get(0).path("chapterTitle").asText()).isEqualTo("The Delhi Sultans");
        assertThat(data(getAs("/archives/lesson-plans", teacher, "q", "delhi sultans")).size()).isEqualTo(1);
        assertThat(data(getAs("/archives/lesson-plans", teacher, "q", "photosynthesis")).size()).isZero();
        JsonNode syllabus = data(getAs("/archives/syllabus", teacher, "academicYearId", last.getAcademicYearId().toString()).andExpect(status().isOk()));
        assertThat(syllabus.get(0).path("topics").size()).isEqualTo(2);
        assertThat(syllabus.get(0).path("topics").get(0).path("archived").asBoolean()).isTrue();
        getAs("/archives/lesson-plans", parentToken()).andExpect(status().isForbidden());
        postJson("/archives", token("ACADEMIC_COORDINATOR"), Map.of("academicYearId", UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
