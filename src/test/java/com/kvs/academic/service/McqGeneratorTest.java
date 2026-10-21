package com.kvs.academic.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the chapter-text question generator (US-06006). */
class McqGeneratorTest {

    private static final String TEXT = "Photosynthesis takes place in the chloroplasts of green leaves. "
            + "Chlorophyll absorbs sunlight and gives leaves their green colour. "
            + "Plants take in carbon dioxide through tiny pores called stomata. "
            + "Water reaches the leaves from the roots through the xylem vessels. "
            + "Glucose made in the leaves is stored as starch in many plants. "
            + "Oxygen is released into the atmosphere as a by-product of photosynthesis.";

    @Test
    @DisplayName("Each question blanks one key term of its sentence and offers it among four distinct options")
    void blanksKeyTerms() {
        McqGenerator.Result result = McqGenerator.generate(TEXT, 10);
        assertThat(result.sentencesScanned()).isEqualTo(6);
        assertThat(result.questions()).hasSizeGreaterThanOrEqualTo(4);
        for (McqGenerator.Generated q : result.questions()) {
            String answer = q.options().get(q.correctIndex());
            assertThat(q.questionText()).contains("______").doesNotContain(" " + answer + " ");
            assertThat(q.explanation()).contains(answer);
            assertThat(new HashSet<>(q.options())).hasSize(4);
        }
    }

    @Test
    @DisplayName("Generation is deterministic and respects the requested count")
    void deterministicAndCapped() {
        assertThat(McqGenerator.generate(TEXT, 2).questions()).hasSize(2);
        assertThat(McqGenerator.generate(TEXT, 3)).isEqualTo(McqGenerator.generate(TEXT, 3));
    }

    @Test
    @DisplayName("Years get nearby years as distractors")
    void numberDistractors() {
        McqGenerator.Result result = McqGenerator.generate("The battle was fought in 1757 near a river. "
                + "The treaty was signed in 1765 by the two sides. The famine came in 1770 and many people died.", 5);
        McqGenerator.Generated year = result.questions().stream()
                .filter(q -> q.options().contains("1757") && q.questionText().contains("fought")).findFirst().orElseThrow();
        assertThat(year.options()).allMatch(o -> o.matches("\\d{4}"));
    }

    @Test
    @DisplayName("Text without distinct key terms yields no questions")
    void nothingToAsk() {
        assertThat(McqGenerator.generate("it is what it is and so it goes on and on for us all. ".repeat(6), 5).questions()).isEmpty();
    }
}
