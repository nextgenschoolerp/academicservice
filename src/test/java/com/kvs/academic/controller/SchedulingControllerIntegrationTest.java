package com.kvs.academic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Module 06 scheduling: bell schedules (US-06000), workload caps and allocations (US-06001), the timetable generator and
 * manual moves (US-06002), proxy substitution (US-06003) and the faculty timetable PDF (US-06015).
 */
class SchedulingControllerIntegrationTest extends AcademicTestSupport {

    private String neha;
    private String ravi;
    private String meera;
    private String sunil;
    private String eng;
    private String math;
    private String sci;
    private String scheduleId;

    @BeforeEach
    void setUp() throws Exception {
        neha = "t-neha-" + run;
        ravi = "t-ravi-" + run;
        meera = "t-meera-" + run;
        sunil = "t-sunil-" + run;
        scheduleId = bellSchedule();
        eng = subject("ENG", "English");
        math = subject("MATH", "Mathematics");
        sci = subject("SCI", "Science");
        workload(neha, "Neha Kapoor", 30, 6, "MATH", "SCI");
        workload(ravi, "Ravi Iyer", 30, 6, "ENG");
        workload(meera, "Meera Joshi", 30, 6, "ENG", "MATH");
        workload(sunil, "Sunil Das", 30, 6, "SCI");
    }

    private void allocateBoth() throws Exception {
        allocate(sectionA, math, neha, 6);
        allocate(sectionA, eng, ravi, 5);
        allocate(sectionA, sci, sunil, 4);
        allocate(sectionB, math, neha, 6);
        allocate(sectionB, eng, meera, 5);
        allocate(sectionB, sci, sunil, 4);
    }

    private JsonNode generate(Object... sections) throws Exception {
        return data(postJson("/timetables/generate", token("TIMETABLE_INCHARGE"), Map.of("bellScheduleId", scheduleId,
                "sectionIds", List.of(sections))).andExpect(status().isOk()));
    }

    // ------------------------------------------------------------------ US-06000

    @Test
    @DisplayName("US-06000: bell schedule totals and trace id; overlapping slots 400; no teaching period 400; 401; 403; 404 other tenant")
    void bellScheduleRules() throws Exception {
        JsonNode schedule = data(getAs("/bell-schedules/" + scheduleId, token("SUBJECT_TEACHER")).andExpect(status().isOk()));
        assertThat(schedule.path("teachingPeriodsPerDay").asInt()).isEqualTo(4);
        assertThat(schedule.path("teachingPeriodsPerWeek").asInt()).isEqualTo(20);
        assertThat(schedule.path("teachingMinutesPerDay").asLong()).isEqualTo(180);
        assertThat(schedule.path("active").asBoolean()).isTrue();
        assertThat(schedule.path("slots").get(3).path("slotType").asText()).isEqualTo("RECESS");
        assertThat(schedule.path("slots").get(4).path("periodNumber").asInt()).isEqualTo(3);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE + "/bell-schedules")
                        .header("Authorization", token("TIMETABLE_INCHARGE")).header("X-Trace-Id", "trace-academic-001"))
                .andExpect(status().isOk()).andExpect(header().string("X-Trace-Id", "trace-academic-001"));

        var overlap = postJson("/bell-schedules", token("ACADEMIC_COORDINATOR"), Map.of("branchId", "BR-1", "scheduleName", "Bad",
                "workingDays", List.of(1), "slots", List.of(slot("PERIOD", "P1", "08:00", "08:45"), slot("RECESS", "Break", "08:30", "08:50"))))
                .andExpect(status().isBadRequest());
        assertThat(errorCode(overlap)).isEqualTo("OVERLAPPING_SLOTS");
        postJson("/bell-schedules", token("ACADEMIC_COORDINATOR"), Map.of("branchId", "BR-1", "scheduleName", "Breaks only",
                "workingDays", List.of(1), "slots", List.of(slot("RECESS", "Break", "08:30", "08:50")))).andExpect(status().isBadRequest());
        postJson("/bell-schedules", token("ACADEMIC_COORDINATOR"), Map.of("branchId", "BR-1", "scheduleName", "", "workingDays", List.of(),
                "slots", List.of())).andExpect(status().isBadRequest());

        getAs("/bell-schedules", null).andExpect(status().isUnauthorized());
        postJson("/bell-schedules", token("SUBJECT_TEACHER"), Map.of("branchId", "BR-1", "scheduleName", "Mine", "workingDays", List.of(1),
                "slots", List.of(slot("PERIOD", "P1", "08:00", "08:45")))).andExpect(status().isForbidden());
        getAs("/bell-schedules/" + UUID.randomUUID(), token("TIMETABLE_INCHARGE")).andExpect(status().isNotFound());
        getAs("/bell-schedules/" + scheduleId, token("TIMETABLE_INCHARGE", "x", "OTHER-TENANT")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ US-06001

    @Test
    @DisplayName("US-06001: caps and utilisation; over the weekly cap 409; not qualified 409; lowering a cap below allocations 409; duplicate subject 409")
    void workloadCaps() throws Exception {
        allocate(sectionA, math, neha, 6);
        allocate(sectionB, math, neha, 6);
        JsonNode nehaLoad = null;
        for (JsonNode w : data(getAs("/workloads", token("SUBJECT_TEACHER")).andExpect(status().isOk()))) {
            if (w.path("teacherId").asText().equals(neha)) {
                nehaLoad = w;
            }
        }
        assertThat(nehaLoad).isNotNull();
        assertThat(nehaLoad.path("allocatedPeriods").asInt()).isEqualTo(12);
        assertThat(nehaLoad.path("remainingPeriods").asInt()).isEqualTo(18);
        assertThat(nehaLoad.path("utilisationPercent").asDouble()).isEqualTo(40.0);

        workload("t-tight-" + run, "Tight Cap", 8, 4, "SCI");
        allocate(sectionA, sci, "t-tight-" + run, 6);
        var over = postJson("/allocations", token("TIMETABLE_INCHARGE"), Map.of("sectionId", sectionB.getSectionId(), "subjectId", sci,
                "teacherId", "t-tight-" + run, "periodsPerWeek", 4)).andExpect(status().isConflict());
        assertThat(errorCode(over)).isEqualTo("WORKLOAD_CAP_EXCEEDED");
        // replacing the same allocation is measured without its old periods
        postJson("/allocations", token("TIMETABLE_INCHARGE"), Map.of("sectionId", sectionA.getSectionId(), "subjectId", sci,
                "teacherId", "t-tight-" + run, "periodsPerWeek", 8)).andExpect(status().isCreated());

        var unqualified = postJson("/allocations", token("TIMETABLE_INCHARGE"), Map.of("sectionId", sectionA.getSectionId(), "subjectId", eng,
                "teacherId", neha, "periodsPerWeek", 2)).andExpect(status().isConflict());
        assertThat(errorCode(unqualified)).isEqualTo("TEACHER_NOT_QUALIFIED");
        putJson("/workloads", token("TIMETABLE_INCHARGE"), Map.of("teacherId", neha, "teacherName", "Neha Kapoor", "weeklyMaxPeriods", 10,
                "dailyMaxPeriods", 4)).andExpect(status().isConflict());
        putJson("/workloads", token("TIMETABLE_INCHARGE"), Map.of("teacherId", neha, "teacherName", "Neha", "weeklyMaxPeriods", 0,
                "dailyMaxPeriods", 4)).andExpect(status().isBadRequest());
        postJson("/subjects", token("TIMETABLE_INCHARGE"), Map.of("subjectCode", "MATH", "subjectName", "Maths")).andExpect(status().isConflict());
        postJson("/allocations", token("TIMETABLE_INCHARGE"), Map.of("sectionId", sectionA.getSectionId(), "subjectId", math,
                "teacherId", "nobody", "periodsPerWeek", 2)).andExpect(status().isNotFound());
        putJson("/workloads", token("SUBJECT_TEACHER"), Map.of("teacherId", neha, "teacherName", "Neha", "weeklyMaxPeriods", 40,
                "dailyMaxPeriods", 8)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ US-06002

    @Test
    @DisplayName("US-06002: generates both sections with no clashes, at most 2 periods of a subject a day, and spread subjects")
    void generatorIsConflictFree() throws Exception {
        allocateBoth();
        JsonNode report = generate(sectionA.getSectionId(), sectionB.getSectionId());
        assertThat(report.path("periodsRequired").asInt()).isEqualTo(30);
        assertThat(report.path("periodsPlaced").asInt()).isEqualTo(30);
        assertThat(report.path("sections").get(0).path("unplaced").size()).isZero();
        assertThat(report.path("sections").get(0).path("freePeriods").asInt()).isEqualTo(5);

        JsonNode nehaWeek = data(getAs("/timetables/teachers/" + neha, token("SUBJECT_TEACHER")).andExpect(status().isOk()));
        assertThat(nehaWeek.path("periodsPerWeek").asInt()).isEqualTo(12);
        Set<String> slots = new HashSet<>();
        for (JsonNode e : nehaWeek.path("entries")) {
            assertThat(slots.add(e.path("dayOfWeek").asInt() + "/" + e.path("periodNumber").asInt())).as("Neha double-booked").isTrue();
        }
        JsonNode classA = data(getAs("/timetables/sections/" + sectionA.getSectionId(), token("CLASS_TEACHER")).andExpect(status().isOk()));
        assertThat(classA.path("className").asText()).isEqualTo("Class 7-A");
        Map<String, Integer> perDay = new HashMap<>();
        Set<Integer> mathDays = new HashSet<>();
        for (JsonNode e : classA.path("entries")) {
            perDay.merge(e.path("subjectCode").asText() + e.path("dayOfWeek").asInt(), 1, Integer::sum);
            if (e.path("subjectCode").asText().equals("MATH")) {
                mathDays.add(e.path("dayOfWeek").asInt());
            }
        }
        assertThat(perDay.values()).allMatch(n -> n <= 2);
        assertThat(mathDays).hasSize(5);

        // regenerating one section keeps the other section's periods as fixed commitments
        generate(sectionA.getSectionId());
        nehaWeek = data(getAs("/timetables/teachers/" + neha, token("SUBJECT_TEACHER")));
        slots.clear();
        for (JsonNode e : nehaWeek.path("entries")) {
            assertThat(slots.add(e.path("dayOfWeek").asInt() + "/" + e.path("periodNumber").asInt())).isTrue();
        }
        assertThat(nehaWeek.path("periodsPerWeek").asInt()).isEqualTo(12);
    }

    @Test
    @DisplayName("US-06002: periods that break a teacher's daily cap are reported, not forced; generator 403 for a subject teacher")
    void generatorReportsUnplaced() throws Exception {
        workload("t-once-" + run, "Once A Day", 10, 1, "SCI");
        allocate(sectionA, sci, "t-once-" + run, 6);
        JsonNode report = generate(sectionA.getSectionId());
        assertThat(report.path("periodsPlaced").asInt()).isEqualTo(5);
        JsonNode unplaced = report.path("sections").get(0).path("unplaced").get(0);
        assertThat(unplaced.path("missingPeriods").asInt()).isEqualTo(1);
        assertThat(unplaced.path("reason").asText()).contains("daily cap");
        postJson("/timetables/generate", token("SUBJECT_TEACHER"), Map.of("bellScheduleId", scheduleId, "sectionIds", List.of(sectionA.getSectionId())))
                .andExpect(status().isForbidden());
        postJson("/timetables/generate", token("TIMETABLE_INCHARGE"), Map.of("bellScheduleId", scheduleId, "sectionIds", List.of()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("US-06002: moving a period onto a taken slot or a busy teacher is refused (409); a free slot works")
    void moveEntry() throws Exception {
        // Neha is the only teacher of both sections, so each of her 7-B periods is a free 7-A slot where she is busy
        allocate(sectionA, math, neha, 6);
        allocate(sectionB, math, neha, 6);
        generate(sectionA.getSectionId(), sectionB.getSectionId());
        JsonNode entries = data(getAs("/timetables/sections/" + sectionA.getSectionId(), token("TIMETABLE_INCHARGE"))).path("entries");
        JsonNode first = entries.get(0);
        JsonNode other = entries.get(1);
        var taken = putJson("/timetables/entries/" + first.path("timetableId").asText(), token("TIMETABLE_INCHARGE"),
                Map.of("dayOfWeek", other.path("dayOfWeek").asInt(), "periodNumber", other.path("periodNumber").asInt()))
                .andExpect(status().isConflict());
        assertThat(errorCode(taken)).isEqualTo("SECTION_SLOT_TAKEN");

        Set<String> inA = new HashSet<>();
        entries.forEach(e -> inA.add(e.path("dayOfWeek").asInt() + "/" + e.path("periodNumber").asInt()));
        JsonNode bPeriod = data(getAs("/timetables/sections/" + sectionB.getSectionId(), token("TIMETABLE_INCHARGE"))).path("entries").get(0);
        var clash = putJson("/timetables/entries/" + first.path("timetableId").asText(), token("TIMETABLE_INCHARGE"),
                Map.of("dayOfWeek", bPeriod.path("dayOfWeek").asInt(), "periodNumber", bPeriod.path("periodNumber").asInt()))
                .andExpect(status().isConflict());
        assertThat(errorCode(clash)).isEqualTo("TEACHER_DOUBLE_BOOKED");
        var unqualified = putJson("/timetables/entries/" + first.path("timetableId").asText(), token("TIMETABLE_INCHARGE"),
                Map.of("dayOfWeek", first.path("dayOfWeek").asInt(), "periodNumber", first.path("periodNumber").asInt(), "teacherId", ravi))
                .andExpect(status().isConflict());
        assertThat(errorCode(unqualified)).isEqualTo("TEACHER_NOT_QUALIFIED");

        Set<String> inB = new HashSet<>();
        data(getAs("/timetables/sections/" + sectionB.getSectionId(), token("TIMETABLE_INCHARGE"))).path("entries")
                .forEach(e -> inB.add(e.path("dayOfWeek").asInt() + "/" + e.path("periodNumber").asInt()));
        int day = 0;
        int period = 0;
        for (int d = 1; d <= 5 && day == 0; d++) {
            for (int p = 1; p <= 4; p++) {
                if (!inA.contains(d + "/" + p) && !inB.contains(d + "/" + p)) {
                    day = d;
                    period = p;
                    break;
                }
            }
        }
        JsonNode moved = data(putJson("/timetables/entries/" + first.path("timetableId").asText(), token("TIMETABLE_INCHARGE"),
                Map.of("dayOfWeek", day, "periodNumber", period, "teacherId", meera)).andExpect(status().isOk()));
        assertThat(moved.path("dayOfWeek").asInt()).isEqualTo(day);
        assertThat(moved.path("periodNumber").asInt()).isEqualTo(period);
        assertThat(moved.path("teacherName").asText()).isEqualTo("Meera Joshi");
        putJson("/timetables/entries/" + first.path("timetableId").asText(), token("TIMETABLE_INCHARGE"),
                Map.of("dayOfWeek", 6, "periodNumber", 1)).andExpect(status().isBadRequest());
        putJson("/timetables/entries/" + UUID.randomUUID(), token("TIMETABLE_INCHARGE"), Map.of("dayOfWeek", 1, "periodNumber", 1))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US-06002: a parent sees their own child's timetable (200) but not another child's (403)")
    void parentTimetable() throws Exception {
        allocateBoth();
        generate(sectionA.getSectionId());
        assertThat(data(getAs("/timetables/students/" + asha.getStudentId(), parentToken()).andExpect(status().isOk()))
                .path("periodsPerWeek").asInt()).isEqualTo(15);
        getAs("/timetables/students/" + bilal.getStudentId(), parentToken()).andExpect(status().isForbidden());
        getAs("/timetables/sections/" + sectionA.getSectionId(), parentToken()).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ US-06003

    @Test
    @DisplayName("US-06003: auto-assign covers every period of an absent teacher with a free, qualified proxy; rerun keeps them; manual rules")
    void proxySubstitution() throws Exception {
        allocateBoth();
        generate(sectionA.getSectionId(), sectionB.getSectionId());
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        JsonNode absence = data(postJson("/substitutions/absences", token("SUBSTITUTION_INCHARGE"),
                Map.of("teacherId", neha, "absenceDate", monday.toString(), "reason", "Fever")).andExpect(status().isCreated()));
        int affected = absence.path("periodsAffected").asInt();
        assertThat(affected).isGreaterThan(0);
        postJson("/substitutions/absences", token("SUBSTITUTION_INCHARGE"), Map.of("teacherId", neha, "absenceDate", monday.toString()))
                .andExpect(status().isConflict());
        postJson("/substitutions/absences", token("SUBSTITUTION_INCHARGE"), Map.of("teacherId", "ghost", "absenceDate", monday.toString()))
                .andExpect(status().isNotFound());

        JsonNode sheet = data(postJson("/substitutions/auto-assign", token("SUBSTITUTION_INCHARGE"), Map.of("date", monday.toString()))
                .andExpect(status().isOk()));
        assertThat(sheet.path("periodsToCover").asInt()).isEqualTo(affected);
        assertThat(sheet.path("assigned").asInt()).isEqualTo(affected);
        assertThat(sheet.path("uncovered").asInt()).isZero();
        Map<String, List<String>> busy = new HashMap<>();
        for (JsonNode s : sheet.path("substitutions")) {
            String proxy = s.path("proxyTeacherId").asText();
            assertThat(proxy).isNotEqualTo(neha);
            if (s.path("subjectCode").asText().equals("MATH")) {
                // Meera is the only other MATH-qualified teacher; she takes MATH periods whenever she is free
                assertThat(s.path("matchReason").asText()).contains("free in period");
            }
            busy.computeIfAbsent(proxy, k -> new ArrayList<>()).add(s.path("startTime").asText());
        }
        // nobody covers two classes at once, and nobody covers a period they teach themselves
        busy.values().forEach(times -> assertThat(new HashSet<>(times)).hasSameSizeAs(times));
        for (JsonNode s : sheet.path("substitutions")) {
            JsonNode own = data(getAs("/timetables/teachers/" + s.path("proxyTeacherId").asText(), token("SUBSTITUTION_INCHARGE")));
            for (JsonNode e : own.path("entries")) {
                if (e.path("dayOfWeek").asInt() == 1) {
                    assertThat(e.path("startTime").asText()).isNotEqualTo(s.path("startTime").asText());
                }
            }
        }

        JsonNode again = data(postJson("/substitutions/auto-assign", token("SUBSTITUTION_INCHARGE"), Map.of("date", monday.toString())));
        assertThat(again.path("substitutions").size()).isEqualTo(affected);

        JsonNode firstSub = sheet.path("substitutions").get(0);
        // the proxy of another period at the same time is busy then; pick a teacher teaching at that time instead
        String busyTeacher = null;
        for (JsonNode e : data(getAs("/timetables/sections/" + sectionB.getSectionId(), token("TIMETABLE_INCHARGE"))).path("entries")) {
            if (e.path("dayOfWeek").asInt() == 1 && e.path("startTime").asText().equals(firstSub.path("startTime").asText())
                    && !e.path("teacherId").asText().equals(neha)) {
                busyTeacher = e.path("teacherId").asText();
            }
        }
        if (busyTeacher != null) {
            var notFree = putJson("/substitutions/" + firstSub.path("proxyId").asText(), token("SUBSTITUTION_INCHARGE"),
                    Map.of("proxyTeacherId", busyTeacher)).andExpect(status().isConflict());
            assertThat(errorCode(notFree)).isEqualTo("PROXY_NOT_AVAILABLE");
        }
        JsonNode cancelled = data(putJson("/substitutions/" + firstSub.path("proxyId").asText(), token("SUBSTITUTION_INCHARGE"),
                Map.of("cancel", true)).andExpect(status().isOk()));
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("assignmentMode").asText()).isEqualTo("MANUAL");
        // regenerating the timetables replaces the periods, so the upcoming proxies are cleared and must be assigned again
        JsonNode regenerated = generate(sectionA.getSectionId(), sectionB.getSectionId());
        assertThat(regenerated.path("substitutionsCleared").asInt()).isEqualTo(affected);
        JsonNode afterRegenerate = data(getAs("/substitutions", token("SUBSTITUTION_INCHARGE"), "date", monday.toString()));
        assertThat(afterRegenerate.path("assigned").asInt()).isZero();
        assertThat(afterRegenerate.path("substitutions").size()).isZero();
        JsonNode reassigned = data(postJson("/substitutions/auto-assign", token("SUBSTITUTION_INCHARGE"), Map.of("date", monday.toString())));
        assertThat(reassigned.path("assigned").asInt()).isEqualTo(reassigned.path("periodsToCover").asInt()).isGreaterThan(0);
        // the fairness count only sees live proxies, not the ones cleared with the old timetable
        assertThat(reassigned.path("substitutions").get(0).path("matchReason").asText()).contains("0 proxies in the last 7 days");

        postJson("/substitutions/auto-assign", token("CLASS_TEACHER"), Map.of("date", monday.toString())).andExpect(status().isForbidden());
        getAs("/substitutions", token("SUBJECT_TEACHER"), "date", monday.toString()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("US-06003: when every teacher is busy or capped, the period is reported UNCOVERED")
    void uncoveredWhenNobodyFree() throws Exception {
        allocate(sectionA, math, neha, 5);
        generate(sectionA.getSectionId());
        // every teacher is absent, so nobody is left to cover Neha's periods
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        for (String t : List.of(neha, ravi, meera, sunil)) {
            postJson("/substitutions/absences", token("SUBSTITUTION_INCHARGE"), Map.of("teacherId", t, "absenceDate", monday.toString()))
                    .andExpect(status().isCreated());
        }
        JsonNode sheet = data(postJson("/substitutions/auto-assign", token("SUBSTITUTION_INCHARGE"), Map.of("date", monday.toString())));
        assertThat(sheet.path("uncovered").asInt()).isEqualTo(sheet.path("periodsToCover").asInt()).isGreaterThan(0);
        assertThat(sheet.path("substitutions").get(0).path("status").asText()).isEqualTo("UNCOVERED");
    }

    // ------------------------------------------------------------------ US-06015

    @Test
    @DisplayName("US-06015: the principal exports the faculty timetable PDF; a subject teacher is refused (403); unknown schedule 404")
    void facultyPdf() throws Exception {
        allocateBoth();
        generate(sectionA.getSectionId(), sectionB.getSectionId());
        var response = getAs("/timetables/faculty/pdf", token("SCHOOL_PRINCIPAL"), "bellScheduleId", scheduleId)
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("faculty-timetable.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(response, 0, 4)).isEqualTo("%PDF");
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(response)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(4);
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertThat(text).contains("Neha Kapoor").contains("Class 7-A").contains("Monday").contains("Recess");
        }
        getAs("/timetables/faculty/pdf", token("SUBJECT_TEACHER"), "bellScheduleId", scheduleId).andExpect(status().isForbidden());
        getAs("/timetables/faculty/pdf", token("SCHOOL_PRINCIPAL"), "bellScheduleId", UUID.randomUUID().toString()).andExpect(status().isNotFound());
        // A client that only accepts JSON gets 406, not a 500
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE + "/timetables/faculty/pdf")
                        .param("bellScheduleId", scheduleId).header("Authorization", token("SCHOOL_PRINCIPAL"))
                        .accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable()).andExpect(jsonPath("$.errorCode").value("NOT_ACCEPTABLE"));
    }
}
