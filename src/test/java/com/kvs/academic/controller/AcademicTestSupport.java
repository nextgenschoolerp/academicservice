package com.kvs.academic.controller;

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
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared fixtures for the Module 06 integration tests. Every test instance gets its own institution (tenant), so data from
 * other tests never interferes. studentservice's read model (academic year, Class 7 with sections A and B, students) is
 * written straight to its tables, as studentservice would have created it.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AcademicTestSupport {

    static final String BASE = "/api/v1/lms";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JwtTokenService jwtTokenService;
    @Autowired protected AcademicSessionRepository sessionRepository;
    @Autowired protected GradeRepository gradeRepository;
    @Autowired protected SectionRepository sectionRepository;
    @Autowired protected StudentMasterRepository studentRepository;

    protected final String run = UUID.randomUUID().toString().substring(0, 8);
    protected final String tenant = "T-ACAD-" + run;
    protected final String parentId = "parent-" + run;

    protected AcademicSession year;
    protected Grade grade;
    protected Section sectionA;
    protected Section sectionB;
    protected StudentMaster asha;
    protected StudentMaster bilal;
    protected StudentMaster chitra;
    protected StudentMaster dev;

    @BeforeEach
    void baseSetUp() {
        LocalDate today = LocalDate.now();
        int start = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        year = sessionRepository.save(AcademicSession.builder().tenantId(tenant).sessionName(start + "-" + (start + 1))
                .startDate(LocalDate.of(start, 4, 1)).endDate(LocalDate.of(start + 1, 3, 31)).isActive(true).build());
        grade = gradeRepository.save(Grade.builder().tenantId(tenant).gradeName("Class 7").gradeCode("G7-" + run).sequenceOrder(10).build());
        sectionA = sectionRepository.save(Section.builder().gradeId(grade.getGradeId()).sectionName("A").maxCapacity(40).build());
        sectionB = sectionRepository.save(Section.builder().gradeId(grade.getGradeId()).sectionName("B").maxCapacity(40).build());
        asha = student("Asha", sectionA, 1, parentId);
        bilal = student("Bilal", sectionA, 2, null);
        chitra = student("Chitra", sectionA, 3, null);
        dev = student("Dev", sectionB, 1, null);
    }

    protected StudentMaster student(String first, Section section, int roll, String parent) {
        return studentRepository.save(StudentMaster.builder().tenantId(tenant).branchId("BR-1")
                .studentUid("AC-" + first.toUpperCase() + "-" + UUID.randomUUID().toString().substring(0, 8))
                .firstName(first).lastName("Test").dateOfBirth(LocalDate.of(2013, 6, 1)).gender(Gender.FEMALE)
                .gradeId(section.getGradeId()).sectionId(section.getSectionId()).rollNumber(roll).parentAccountId(parent)
                .studentStatus(StudentStatus.ACTIVE).build());
    }

    // ------------------------------------------------------------------ tokens

    /** A token for the role whose userId is "<role>-<run>" (teachers are identified by userId). */
    protected String token(String role) {
        return token(role, role.toLowerCase(Locale.ROOT) + "-" + run);
    }

    protected String token(String role, String userId) {
        return token(role, userId, tenant);
    }

    protected String token(String role, String userId, String tenantId) {
        return "Bearer " + jwtTokenService.generateToken(userId + "@kvs.local",
                Map.of("roles", List.of("ROLE_" + role), "tenantId", tenantId, "userId", userId));
    }

    protected String parentToken() {
        return token("APPLICANT_PARENT", parentId);
    }

    // ------------------------------------------------------------------ requests

    protected ResultActions postJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(withBody(post(BASE + path), token, body));
    }

    protected ResultActions putJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(withBody(put(BASE + path), token, body));
    }

    protected ResultActions getAs(String path, String token, String... params) throws Exception {
        MockHttpServletRequestBuilder req = get(BASE + path);
        if (token != null) {
            req = req.header("Authorization", token);
        }
        for (int i = 0; i < params.length; i += 2) {
            req = req.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(req);
    }

    protected ResultActions deleteAs(String path, String token) throws Exception {
        return mockMvc.perform(delete(BASE + path).header("Authorization", token));
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) {
            req = req.header("Authorization", token);
        }
        return req.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : objectMapper.writeValueAsString(body));
    }

    protected JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }

    protected String errorCode(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("errorCode").asText();
    }

    // ------------------------------------------------------------------ common setup through the API

    /** Mon–Fri: assembly, P1, P2, recess, P3, P4 (4 teaching periods a day, 20 a week). */
    protected String bellSchedule() throws Exception {
        return data(postJson("/bell-schedules", token("ACADEMIC_COORDINATOR"), Map.of("branchId", "BR-1", "scheduleName", "Summer timings",
                "workingDays", List.of(1, 2, 3, 4, 5), "slots", List.of(
                        slot("ASSEMBLY", "Assembly", "08:00", "08:15"), slot("PERIOD", "Period 1", "08:15", "09:00"),
                        slot("PERIOD", "Period 2", "09:00", "09:45"), slot("RECESS", "Recess", "09:45", "10:00"),
                        slot("PERIOD", "Period 3", "10:00", "10:45"), slot("PERIOD", "Period 4", "10:45", "11:30"))))
                .andExpect(status().isCreated())).path("bellScheduleId").asText();
    }

    protected static Map<String, Object> slot(String type, String label, String start, String end) {
        return Map.of("slotType", type, "label", label, "startTime", start, "endTime", end);
    }

    protected String subject(String code, String name) throws Exception {
        return data(postJson("/subjects", token("TIMETABLE_INCHARGE"), Map.of("subjectCode", code, "subjectName", name))
                .andExpect(status().isCreated())).path("subjectId").asText();
    }

    protected void workload(String teacherId, String name, int weekly, int daily, String... codes) throws Exception {
        putJson("/workloads", token("TIMETABLE_INCHARGE"), Map.of("teacherId", teacherId, "teacherName", name, "weeklyMaxPeriods", weekly,
                "dailyMaxPeriods", daily, "qualifiedSubjectCodes", List.of(codes))).andExpect(status().isOk());
    }

    protected void allocate(Section section, String subjectId, String teacherId, int periods) throws Exception {
        postJson("/allocations", token("TIMETABLE_INCHARGE"), Map.of("sectionId", section.getSectionId(), "subjectId", subjectId,
                "teacherId", teacherId, "periodsPerWeek", periods)).andExpect(status().isCreated());
    }
}
