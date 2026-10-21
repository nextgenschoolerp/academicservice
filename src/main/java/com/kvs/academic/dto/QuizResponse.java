package com.kvs.academic.dto;

import com.kvs.academic.enums.QuizStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A quiz with its rules and question bank. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponse {
    private UUID quizId;

    private String quizTitle;

    private UUID gradeId;

    private UUID sectionId;

    private UUID subjectId;

    private String subjectCode;

    private String teacherId;

    private QuizStatus status;

    private Integer durationMinutes;

    private Boolean shuffleQuestions;

    private Boolean shuffleOptions;

    private Integer maxTabSwitches;

    private Integer attemptsAllowed;

    private Instant availableFrom;

    private Instant availableUntil;

    private Instant publishedAt;

    private Integer questionCount;

    private Integer totalMarks;

    private List<QuestionResponse> questions;
}
