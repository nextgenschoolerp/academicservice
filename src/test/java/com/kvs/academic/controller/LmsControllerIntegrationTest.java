package com.kvs.academic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kvs.academic.entity.QuizAttempt;
import com.kvs.academic.repo.QuizAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Module 06 LMS: asset upload (US-06005), question generation (US-06006), proctored attempts (US-06007),
 * quiz timer and shuffle rules (US-06014) and lecture watch time (US-06018).
 */
class LmsControllerIntegrationTest extends AcademicTestSupport {

    static final String CHAPTER = "The Mughal Empire was founded by Babur in 1526 after the First Battle of Panipat. "
            + "Humayun lost the throne to Sher Shah Suri in 1540 and regained it fifteen years later. "
            + "Akbar expanded the empire across northern India and introduced the mansabdari administration. "
            + "Akbar moved his court to Fatehpur Sikri, a city he built near Agra. "
            + "Jahangir allowed the English East India Company to trade at Surat in 1613. "
            + "Shah Jahan built the Taj Mahal at Agra in memory of Mumtaz Mahal. "
            + "Aurangzeb extended Mughal rule into the Deccan but faced resistance from Shivaji and the Marathas. "
            + "After the death of Aurangzeb in 1707 the provinces of Bengal, Awadh and Hyderabad became independent.";

    @Autowired private QuizAttemptRepository attemptRepository;

    private String sst;
    private String teacher;

    @BeforeEach
    void setUp() throws Exception {
        sst = subject("SST", "Social Science");
        teacher = token("SUBJECT_TEACHER", "t-neha-" + run);
    }

    private ResultActions upload(String token, String name, String type, byte[] bytes, String... params) throws Exception {
        var req = multipart(BASE + "/assets").file(new MockMultipartFile("file", name, type, bytes))
                .param("gradeId", grade.getGradeId().toString()).param("subjectId", sst).header("Authorization", token);
        for (int i = 0; i < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(req);
    }

    private static byte[] zip(String... entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String e : entries) {
                zip.putNextEntry(new ZipEntry(e));
                zip.write("<manifest/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ US-06005

    @Test
    @DisplayName("US-06005: PDF, MP4 and SCORM uploads (201) with download; type and content checks 400; parent 403 to upload, sees own child's class")
    void uploadAssets() throws Exception {
        byte[] pdf = "%PDF-1.4\nchapter notes".getBytes(StandardCharsets.UTF_8);
        JsonNode notes = data(upload(token("ELEARNING_ADMIN"), "..\\..\\Mughals.pdf", "application/pdf", pdf, "title", "Mughal notes",
                "chapter", "The Mughal Empire").andExpect(status().isCreated()));
        assertThat(notes.path("assetType").asText()).isEqualTo("PDF");
        assertThat(notes.path("fileName").asText()).isEqualTo("Mughals.pdf");
        assertThat(notes.path("checksumSha256").asText()).hasSize(64);
        byte[] downloaded = getAs("/assets/" + notes.path("assetId").asText() + "/content", parentToken())
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(pdf);

        byte[] mp4 = new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'};
        upload(teacher, "lecture.mp4", "video/mp4", mp4, "title", "Lecture").andExpect(status().isBadRequest());
        assertThat(data(upload(teacher, "lecture.mp4", "video/mp4", mp4, "title", "Lecture", "durationSeconds", "900")
                .andExpect(status().isCreated())).path("durationSeconds").asInt()).isEqualTo(900);
        assertThat(data(upload(token("ELEARNING_ADMIN"), "module.zip", "application/zip", zip("imsmanifest.xml", "index.html"), "title", "SCORM")
                .andExpect(status().isCreated())).path("assetType").asText()).isEqualTo("SCORM");

        assertThat(errorCode(upload(teacher, "fake.pdf", "application/pdf", "MZ not a pdf".getBytes(), "title", "Fake")
                .andExpect(status().isBadRequest()))).isEqualTo("CONTENT_MISMATCH");
        assertThat(errorCode(upload(teacher, "run.exe", "application/x-msdownload", "MZ".getBytes(), "title", "Exe")
                .andExpect(status().isBadRequest()))).isEqualTo("UNSUPPORTED_FILE_TYPE");
        assertThat(errorCode(upload(teacher, "bad.zip", "application/zip", zip("index.html"), "title", "No manifest")
                .andExpect(status().isBadRequest()))).isEqualTo("INVALID_SCORM_PACKAGE");
        upload(teacher, "big.pdf", "application/pdf", ("%PDF" + "x".repeat(1_100_000)).getBytes(), "title", "Big")
                .andExpect(status().isBadRequest());
        upload(parentToken(), "n.pdf", "application/pdf", pdf, "title", "Parent").andExpect(status().isForbidden());
        postJson("/assets/links", teacher, Map.of("title", "Video", "gradeId", grade.getGradeId(), "subjectId", sst, "url", "http://insecure"))
                .andExpect(status().isBadRequest());

        assertThat(data(getAs("/assets", parentToken(), "studentId", asha.getStudentId().toString()).andExpect(status().isOk())).size()).isEqualTo(3);
        getAs("/assets", parentToken(), "studentId", bilal.getStudentId().toString()).andExpect(status().isForbidden());
        getAs("/assets", parentToken()).andExpect(status().isBadRequest());
        assertThat(data(getAs("/assets", teacher, "gradeId", grade.getGradeId().toString(), "assetType", "PDF")).size()).isEqualTo(1);
        getAs("/assets/" + UUID.randomUUID() + "/content", teacher).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ US-06018

    @Test
    @DisplayName("US-06018: continuous playback reaches completion, a skip ahead does not count; section report; only the parent app records (403)")
    void watchTime() throws Exception {
        String video = data(postJson("/assets/links", token("ELEARNING_ADMIN"), Map.of("title", "Mughal architecture", "gradeId", grade.getGradeId(),
                "subjectId", sst, "assetType", "VIDEO", "url", "https://videos.school.test/mughal", "durationSeconds", 600))
                .andExpect(status().isCreated())).path("assetId").asText();
        String path = "/assets/" + video + "/watch-progress";
        JsonNode half = data(postJson(path, parentToken(), Map.of("studentId", asha.getStudentId(), "positionSeconds", 300, "watchedSeconds", 300))
                .andExpect(status().isOk()));
        assertThat(half.path("completionPercent").asDouble()).isEqualTo(50.0);
        JsonNode skipped = data(postJson(path, parentToken(), Map.of("studentId", asha.getStudentId(), "positionSeconds", 590, "watchedSeconds", 10)));
        assertThat(skipped.path("furthestPositionSeconds").asInt()).isEqualTo(310);
        assertThat(skipped.path("completed").asBoolean()).isFalse();
        JsonNode done = data(postJson(path, parentToken(), Map.of("studentId", asha.getStudentId(), "positionSeconds", 590, "watchedSeconds", 280)));
        assertThat(done.path("completed").asBoolean()).isTrue();
        assertThat(done.path("watchedSeconds").asInt()).isEqualTo(590);

        JsonNode report = data(getAs("/assets/" + video + "/watch-report", teacher, "sectionId", sectionA.getSectionId().toString())
                .andExpect(status().isOk()));
        assertThat(report.path("students").asInt()).isEqualTo(3);
        assertThat(report.path("started").asInt()).isEqualTo(1);
        assertThat(report.path("completed").asInt()).isEqualTo(1);
        assertThat(report.path("completionRatePercent").asDouble()).isEqualTo(33.3);

        postJson(path, teacher, Map.of("studentId", asha.getStudentId(), "positionSeconds", 10)).andExpect(status().isForbidden());
        postJson(path, parentToken(), Map.of("studentId", bilal.getStudentId(), "positionSeconds", 10)).andExpect(status().isForbidden());
        postJson(path, parentToken(), Map.of("positionSeconds", 10)).andExpect(status().isBadRequest());
        String link = data(postJson("/assets/links", teacher, Map.of("title", "Reading", "gradeId", grade.getGradeId(), "subjectId", sst,
                "url", "https://example.org/reading"))).path("assetId").asText();
        postJson("/assets/" + link + "/watch-progress", parentToken(), Map.of("studentId", asha.getStudentId(), "positionSeconds", 10))
                .andExpect(status().isBadRequest());
        getAs("/assets/" + video + "/watch-report", parentToken(), "sectionId", sectionA.getSectionId().toString()).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ US-06006

    @Test
    @DisplayName("US-06006: questions generated from chapter text (fill in the blank, 4 options, answer from the text); too short 400; no key terms 400")
    void generateQuestions() throws Exception {
        String quizId = data(postJson("/quizzes", teacher, Map.of("gradeId", grade.getGradeId(), "subjectId", sst, "quizTitle", "Mughals quiz"))
                .andExpect(status().isCreated())).path("quizId").asText();
        JsonNode result = data(postJson("/quizzes/" + quizId + "/questions/generate", teacher,
                Map.of("chapterText", CHAPTER, "count", 5, "topic", "Mughal Empire")).andExpect(status().isCreated()));
        assertThat(result.path("generated").asInt()).isBetween(3, 5);
        for (JsonNode q : result.path("questions")) {
            assertThat(q.path("questionText").asText()).startsWith("Fill in the blank:").contains("______");
            assertThat(q.path("options").size()).isEqualTo(4);
            String answer = q.path("options").get(q.path("correctOptionIndex").asInt()).asText();
            assertThat(q.path("explanation").asText()).contains(answer);
            assertThat(q.path("source").asText()).isEqualTo("GENERATED");
            Set<String> distinct = new HashSet<>();
            q.path("options").forEach(o -> distinct.add(o.asText()));
            assertThat(distinct).hasSize(4);
        }
        assertThat(data(getAs("/quizzes/" + quizId, teacher)).path("questionCount").asInt()).isEqualTo(result.path("generated").asInt());

        postJson("/quizzes/" + quizId + "/questions/generate", teacher, Map.of("chapterText", "Too short.", "count", 5)).andExpect(status().isBadRequest());
        var none = postJson("/quizzes/" + quizId + "/questions/generate", teacher,
                Map.of("chapterText", "we can see it and it is so. ".repeat(10), "count", 3)).andExpect(status().isBadRequest());
        assertThat(errorCode(none)).isEqualTo("NOT_ENOUGH_KEY_TERMS");
        postJson("/quizzes/" + quizId + "/questions/generate", parentToken(), Map.of("chapterText", CHAPTER, "count", 5)).andExpect(status().isForbidden());
        postJson("/quizzes/" + UUID.randomUUID() + "/questions/generate", teacher, Map.of("chapterText", CHAPTER, "count", 5)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ US-06014 + US-06007

    private String publishedQuiz(int maxTabSwitches) throws Exception {
        String quizId = data(postJson("/quizzes", teacher, Map.of("gradeId", grade.getGradeId(), "subjectId", sst, "quizTitle", "Capitals")))
                .path("quizId").asText();
        postJson("/quizzes/" + quizId + "/publish", teacher, Map.of()).andExpect(status().isConflict());
        for (String[] q : List.of(new String[]{"Capital of the Mughals under Akbar?", "Fatehpur Sikri", "Delhi", "Lahore", "Agra"},
                new String[]{"Who built the Taj Mahal?", "Shah Jahan", "Akbar", "Babur", "Humayun"},
                new String[]{"Year of the First Battle of Panipat?", "1526", "1556", "1761", "1857"})) {
            postJson("/quizzes/" + quizId + "/questions", teacher, Map.of("questionText", q[0], "options", List.of(q[1], q[2], q[3], q[4]),
                    "correctOptionIndex", 0, "topic", q[0].contains("Year") ? "Chronology" : "Rulers")).andExpect(status().isCreated());
        }
        postJson("/quizzes/" + quizId + "/questions", teacher, Map.of("questionText", "Bad", "options", List.of("a", "b"), "correctOptionIndex", 2))
                .andExpect(status().isBadRequest());
        postJson("/quizzes/" + quizId + "/questions", teacher, Map.of("questionText", "Dup", "options", List.of("a", "A"), "correctOptionIndex", 0))
                .andExpect(status().isBadRequest());
        putJson("/quizzes/" + quizId + "/settings", teacher, Map.of("durationMinutes", 0, "shuffleQuestions", true, "shuffleOptions", true,
                "maxTabSwitches", 1, "attemptsAllowed", 1)).andExpect(status().isBadRequest());
        putJson("/quizzes/" + quizId + "/settings", teacher, Map.of("durationMinutes", 30, "shuffleQuestions", true, "shuffleOptions", true,
                "maxTabSwitches", 1, "attemptsAllowed", 1, "availableFrom", Instant.now().minusSeconds(60).toString(),
                "availableUntil", Instant.now().plusSeconds(600).toString())).andExpect(status().isBadRequest());
        JsonNode rules = data(putJson("/quizzes/" + quizId + "/settings", teacher, Map.of("durationMinutes", 10, "shuffleQuestions", true,
                "shuffleOptions", true, "maxTabSwitches", maxTabSwitches, "attemptsAllowed", 1)).andExpect(status().isOk()));
        assertThat(rules.path("durationMinutes").asInt()).isEqualTo(10);
        postJson("/quizzes/" + quizId + "/publish", teacher, Map.of()).andExpect(status().isOk());
        return quizId;
    }

    /** Displayed option index of the right answer, matched by text against the teacher's view of the quiz. */
    private List<Map<String, Object>> correctAnswers(String quizId, JsonNode attempt) throws Exception {
        Map<String, String> answers = new HashMap<>();
        for (JsonNode q : data(getAs("/quizzes/" + quizId, teacher)).path("questions")) {
            answers.put(q.path("questionId").asText(), q.path("options").get(q.path("correctOptionIndex").asInt()).asText());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode q : attempt.path("questions")) {
            List<String> shown = new ArrayList<>();
            q.path("options").forEach(o -> shown.add(o.asText()));
            out.add(Map.of("questionId", q.path("questionId").asText(), "selectedOption", shown.indexOf(answers.get(q.path("questionId").asText()))));
        }
        return out;
    }

    @Test
    @DisplayName("US-06014/06007: rules lock once started; parent takes the quiz for their child (no answer key), full marks; one attempt only (409)")
    void proctoredAttempt() throws Exception {
        String quizId = publishedQuiz(2);
        postJson("/quizzes/" + quizId + "/questions", teacher, Map.of("questionText", "Late", "options", List.of("a", "b"), "correctOptionIndex", 0))
                .andExpect(status().isConflict());
        JsonNode listed = data(getAs("/quizzes", parentToken(), "studentId", asha.getStudentId().toString()).andExpect(status().isOk()));
        assertThat(listed.size()).isEqualTo(1);
        assertThat(listed.get(0).path("questions").isMissingNode() || listed.get(0).path("questions").isNull()).isTrue();

        JsonNode attempt = data(postJson("/quizzes/" + quizId + "/attempts", parentToken(), Map.of("studentId", asha.getStudentId()))
                .andExpect(status().isCreated()));
        assertThat(attempt.path("questions").size()).isEqualTo(3);
        assertThat(attempt.path("questions").get(0).has("correctOptionIndex")).isFalse();
        assertThat(attempt.path("remainingSeconds").asLong()).isBetween(590L, 600L);
        assertThat(attempt.path("score").isNull()).isTrue();
        // resuming returns the same attempt
        assertThat(data(postJson("/quizzes/" + quizId + "/attempts", parentToken(), Map.of("studentId", asha.getStudentId())))
                .path("attemptId").asText()).isEqualTo(attempt.path("attemptId").asText());
        var locked = putJson("/quizzes/" + quizId + "/settings", teacher, Map.of("durationMinutes", 60, "shuffleQuestions", false,
                "shuffleOptions", false, "maxTabSwitches", 5, "attemptsAllowed", 3)).andExpect(status().isConflict());
        assertThat(errorCode(locked)).isEqualTo("QUIZ_IN_USE");

        String attemptPath = "/attempts/" + attempt.path("attemptId").asText();
        List<Map<String, Object>> answers = correctAnswers(quizId, attempt);
        putJson(attemptPath + "/answers", parentToken(), Map.of("answers", List.of(answers.get(0)))).andExpect(status().isOk());
        putJson(attemptPath + "/answers", parentToken(), Map.of("answers", List.of(Map.of("questionId", UUID.randomUUID(), "selectedOption", 0))))
                .andExpect(status().isBadRequest());
        postJson(attemptPath + "/events", parentToken(), Map.of("eventType", "TAB_SWITCH")).andExpect(status().isOk());
        JsonNode submitted = data(postJson(attemptPath + "/submit", parentToken(), Map.of("answers", answers.subList(1, 3))).andExpect(status().isOk()));
        assertThat(submitted.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(submitted.path("score").asInt()).isEqualTo(3);
        assertThat(submitted.path("flagged").asBoolean()).isFalse();
        postJson(attemptPath + "/submit", parentToken(), Map.of("answers", List.of())).andExpect(status().isConflict());
        var exhausted = postJson("/quizzes/" + quizId + "/attempts", parentToken(), Map.of("studentId", asha.getStudentId())).andExpect(status().isConflict());
        assertThat(errorCode(exhausted)).isEqualTo("ATTEMPTS_EXHAUSTED");

        postJson("/quizzes/" + quizId + "/attempts", parentToken(), Map.of("studentId", bilal.getStudentId())).andExpect(status().isForbidden());
        getAs(attemptPath, token("APPLICANT_PARENT", "someone-else")).andExpect(status().isForbidden());
        postJson("/quizzes/" + quizId + "/attempts", token("CLASS_TEACHER"), Map.of("studentId", bilal.getStudentId())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US-06007: leaving the tab past the limit auto-submits and flags; a late submission closes as TIMED_OUT; proctoring report")
    void proctoringFlags() throws Exception {
        String quizId = publishedQuiz(1);
        JsonNode chitraAttempt = data(postJson("/quizzes/" + quizId + "/attempts", teacher, Map.of("studentId", chitra.getStudentId()))
                .andExpect(status().isCreated()));
        String chitraPath = "/attempts/" + chitraAttempt.path("attemptId").asText();
        putJson(chitraPath + "/answers", teacher, Map.of("answers", correctAnswers(quizId, chitraAttempt).subList(0, 1))).andExpect(status().isOk());
        assertThat(data(postJson(chitraPath + "/events", teacher, Map.of("eventType", "WINDOW_BLUR"))).path("status").asText()).isEqualTo("IN_PROGRESS");
        JsonNode auto = data(postJson(chitraPath + "/events", teacher, Map.of("eventType", "TAB_SWITCH", "detail", "visibilitychange")));
        assertThat(auto.path("status").asText()).isEqualTo("AUTO_SUBMITTED");
        assertThat(auto.path("flagged").asBoolean()).isTrue();
        assertThat(auto.path("flagReason").asText()).contains("2 times (limit 1)");
        assertThat(auto.path("score").asInt()).isEqualTo(1);

        // Dev (7-B) can take a class-wide quiz; his timer runs out before he submits
        JsonNode devAttempt = data(postJson("/quizzes/" + quizId + "/attempts", teacher, Map.of("studentId", dev.getStudentId())));
        QuizAttempt stored = attemptRepository.findById(UUID.fromString(devAttempt.path("attemptId").asText())).orElseThrow();
        stored.setDeadlineAt(Instant.now().minusSeconds(120));
        attemptRepository.save(stored);
        JsonNode late = data(postJson("/attempts/" + devAttempt.path("attemptId").asText() + "/submit", teacher,
                Map.of("answers", correctAnswers(quizId, devAttempt))).andExpect(status().isOk()));
        assertThat(late.path("status").asText()).isEqualTo("TIMED_OUT");
        assertThat(late.path("score").asInt()).isZero();

        JsonNode report = data(getAs("/quizzes/" + quizId + "/proctoring-report", token("SCHOOL_PRINCIPAL")).andExpect(status().isOk()));
        assertThat(report.path("attempts").asInt()).isEqualTo(2);
        assertThat(report.path("flagged").asInt()).isEqualTo(2);
        assertThat(report.path("rows").get(0).path("events").asInt()).isEqualTo(2);
        getAs("/quizzes/" + quizId + "/proctoring-report", parentToken()).andExpect(status().isForbidden());
    }
}
