package com.kvs.academic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvs.academic.entity.AcademicSession;
import com.kvs.academic.entity.Grade;
import com.kvs.academic.entity.Section;
import com.kvs.academic.entity.StudentMaster;
import com.kvs.academic.enums.Gender;
import com.kvs.academic.enums.StudentStatus;
import com.kvs.academic.repo.AcademicSessionRepository;
import com.kvs.academic.repo.GradeRepository;
import com.kvs.academic.repo.SectionRepository;
import com.kvs.academic.repo.StudentMasterRepository;
import com.kvs.core.service.JwtTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One school-day flow across the Module 06 scheduling stories, each step by the staff member who does it:
 * the academic coordinator sets the bell schedule (US-06000), the timetable in-charge registers subjects, teacher caps and
 * allocations (US-06001) and generates both Class 6 timetables (US-06002); at morning roll call the substitution in-charge
 * marks a teacher absent and the optimiser covers every period (US-06003); the principal prints the faculty timetable for
 * the staff room (US-06015) and the parent app shows the child's timetable.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AcademicDayEndToEndTest {

    private static final String BASE = "/api/v1/lms";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private AcademicSessionRepository sessionRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private StudentMasterRepository studentRepository;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private final String tenant = "T-DAY-" + run;

    private String token(String role, String userId) {
        return "Bearer " + jwtTokenService.generateToken(userId + "@school.test",
                Map.of("roles", List.of("ROLE_" + role), "tenantId", tenant, "userId", userId));
    }

    private JsonNode call(String method, String path, String token, Object body, ResultMatcher expected) throws Exception {
        var req = switch (method) {
            case "POST" -> post(BASE + path);
            case "PUT" -> put(BASE + path);
            default -> get(BASE + path);
        };
        req.header("Authorization", token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return objectMapper.readTree(mockMvc.perform(req).andExpect(expected).andReturn().getResponse().getContentAsString()).path("data");
    }

    private static Map<String, Object> slot(String type, String label, String start, String end) {
        return Map.of("slotType", type, "label", label, "startTime", start, "endTime", end);
    }

    @Test
    @DisplayName("Bell schedule → workloads → timetables → morning absence → proxies → noticeboard PDF → parent timetable")
    void schoolDay() throws Exception {
        LocalDate today = LocalDate.now();
        int start = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        AcademicSession session = sessionRepository.save(AcademicSession.builder().tenantId(tenant).sessionName(start + "-" + (start + 1))
                .startDate(LocalDate.of(start, 4, 1)).endDate(LocalDate.of(start + 1, 3, 31)).isActive(true).build());
        Grade six = gradeRepository.save(Grade.builder().tenantId(tenant).gradeName("Class 6").gradeCode("G6-" + run).sequenceOrder(9).build());
        Section a = sectionRepository.save(Section.builder().gradeId(six.getGradeId()).sectionName("A").build());
        Section b = sectionRepository.save(Section.builder().gradeId(six.getGradeId()).sectionName("B").build());
        StudentMaster aarav = studentRepository.save(StudentMaster.builder().tenantId(tenant).branchId("BR").studentUid("DAY-" + run)
                .firstName("Aarav").lastName("Sharma").dateOfBirth(LocalDate.of(2014, 2, 2)).gender(Gender.MALE).gradeId(six.getGradeId())
                .sectionId(a.getSectionId()).rollNumber(1).parentAccountId("parent-" + run).studentStatus(StudentStatus.ACTIVE).build());
        assertThat(session.getAcademicYearId()).isNotNull();

        String coordinator = token("ACADEMIC_COORDINATOR", "coordinator-" + run);
        String incharge = token("TIMETABLE_INCHARGE", "timetable-" + run);
        String substitution = token("SUBSTITUTION_INCHARGE", "substitution-" + run);
        String principal = token("SCHOOL_PRINCIPAL", "principal-" + run);

        // US-06000: six periods, assembly, recess and lunch, Monday to Saturday
        JsonNode schedule = call("POST", "/bell-schedules", coordinator, Map.of("branchId", "BR", "scheduleName", "Winter timings",
                "workingDays", List.of(1, 2, 3, 4, 5, 6), "slots", List.of(
                        slot("ASSEMBLY", "Assembly", "08:00", "08:20"), slot("PERIOD", "Period 1", "08:20", "09:00"),
                        slot("PERIOD", "Period 2", "09:00", "09:40"), slot("PERIOD", "Period 3", "09:40", "10:20"),
                        slot("RECESS", "Recess", "10:20", "10:40"), slot("PERIOD", "Period 4", "10:40", "11:20"),
                        slot("PERIOD", "Period 5", "11:20", "12:00"), slot("LUNCH", "Lunch", "12:00", "12:30"),
                        slot("PERIOD", "Period 6", "12:30", "13:10"))), status().isCreated());
        assertThat(schedule.path("teachingPeriodsPerWeek").asInt()).isEqualTo(36);
        String scheduleId = schedule.path("bellScheduleId").asText();

        // US-06001: subjects, six teachers with caps, and the allocations of both sections
        Map<String, String> subjects = new LinkedHashMap<>();
        for (String[] s : List.of(new String[]{"ENG", "English"}, new String[]{"MATH", "Mathematics"}, new String[]{"SCI", "Science"},
                new String[]{"SST", "Social Science"}, new String[]{"HIN", "Hindi"})) {
            subjects.put(s[0], call("POST", "/subjects", incharge, Map.of("subjectCode", s[0], "subjectName", s[1]), status().isCreated())
                    .path("subjectId").asText());
        }
        String[][] teachers = {{"t-priya", "Priya Nair", "ENG,HIN"}, {"t-neha", "Neha Kapoor", "MATH,SCI"}, {"t-vikram", "Vikram Rao", "SCI,MATH"},
                {"t-farah", "Farah Khan", "SST,ENG"}, {"t-arjun", "Arjun Mehta", "HIN,SST"},
                {"t-sana", "Sana Qureshi", "MATH,SCI"}};
        for (String[] t : teachers) {
            call("PUT", "/workloads", incharge, Map.of("teacherId", t[0] + run, "teacherName", t[1], "weeklyMaxPeriods", 28, "dailyMaxPeriods", 6,
                    "qualifiedSubjectCodes", List.of(t[2].split(","))), status().isOk());
        }
        Object[][] plan = {{a, "ENG", "t-priya", 7}, {a, "MATH", "t-neha", 7}, {a, "SCI", "t-vikram", 6}, {a, "SST", "t-farah", 6}, {a, "HIN", "t-arjun", 5},
                {b, "ENG", "t-farah", 7}, {b, "MATH", "t-neha", 7}, {b, "SCI", "t-vikram", 6}, {b, "SST", "t-arjun", 6}, {b, "HIN", "t-priya", 5}};
        for (Object[] p : plan) {
            call("POST", "/allocations", incharge, Map.of("sectionId", ((Section) p[0]).getSectionId(), "subjectId", subjects.get((String) p[1]),
                    "teacherId", p[2] + run, "periodsPerWeek", p[3]), status().isCreated());
        }

        // US-06002: both timetables, every period placed
        JsonNode generated = call("POST", "/timetables/generate", incharge, Map.of("bellScheduleId", scheduleId,
                "sectionIds", List.of(a.getSectionId(), b.getSectionId())), status().isOk());
        assertThat(generated.path("periodsRequired").asInt()).isEqualTo(62);
        assertThat(generated.path("periodsPlaced").asInt()).isEqualTo(62);
        for (String[] t : teachers) {
            Set<String> seen = new HashSet<>();
            for (JsonNode e : call("GET", "/timetables/teachers/" + t[0] + run, incharge, null, status().isOk()).path("entries")) {
                assertThat(seen.add(e.path("dayOfWeek").asInt() + "/" + e.path("periodNumber").asInt())).as(t[1] + " double-booked").isTrue();
            }
        }

        // US-06003: Monday roll call: Neha is absent; every one of her periods gets a free proxy
        LocalDate monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        JsonNode absence = call("POST", "/substitutions/absences", substitution, Map.of("teacherId", "t-neha" + run, "absenceDate", monday.toString(),
                "reason", "Medical leave"), status().isCreated());
        int periods = absence.path("periodsAffected").asInt();
        assertThat(periods).isGreaterThanOrEqualTo(2);
        JsonNode sheet = call("POST", "/substitutions/auto-assign", substitution, Map.of("date", monday.toString()), status().isOk());
        assertThat(sheet.path("assigned").asInt()).isEqualTo(periods);
        assertThat(sheet.path("uncovered").asInt()).isZero();
        long qualified = 0;
        for (JsonNode s : sheet.path("substitutions")) {
            assertThat(s.path("proxyTeacherId").asText()).isNotEqualTo("t-neha" + run);
            qualified += s.path("matchReason").asText().startsWith("Teaches") ? 1 : 0;
        }
        // Sana (MATH, SCI) has no classes, so a qualified teacher is always free: every period goes to a MATH/SCI teacher
        assertThat(qualified).isEqualTo(periods);

        // US-06015: the principal prints the noticeboard copy: one page per teacher
        byte[] pdf = mockMvc.perform(get(BASE + "/timetables/faculty/pdf").param("bellScheduleId", scheduleId).header("Authorization", principal))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(6);
        }

        // the parent app shows Aarav's class timetable
        JsonNode mine = call("GET", "/timetables/students/" + aarav.getStudentId(), token("APPLICANT_PARENT", "parent-" + run), null, status().isOk());
        assertThat(mine.path("className").asText()).isEqualTo("Class 6-A");
        assertThat(mine.path("periodsPerWeek").asInt()).isEqualTo(31);
    }
}
