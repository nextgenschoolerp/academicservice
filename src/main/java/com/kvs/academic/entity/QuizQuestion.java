package com.kvs.academic.entity;

import com.kvs.academic.enums.QuestionSource;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** One multiple-choice question of a quiz's question bank (US-06006). optionsJson is a JSON array of the option texts. */
@Entity
@Table(name = "lms_quiz_questions", indexes = {
        @Index(name = "idx_question_quiz", columnList = "quiz_id, sequence_no")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestion extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "question_text", nullable = false, length = 1000)
    private String questionText;

    @Column(name = "options_json", nullable = false, length = 2000)
    private String optionsJson;

    @Column(name = "correct_option_index", nullable = false)
    private Integer correctOptionIndex;

    @Column(name = "topic", length = 200)
    private String topic;

    @Column(name = "marks", nullable = false)
    @Builder.Default
    private Integer marks = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private QuestionSource source;

    @Column(name = "explanation", length = 1000)
    private String explanation;
}
