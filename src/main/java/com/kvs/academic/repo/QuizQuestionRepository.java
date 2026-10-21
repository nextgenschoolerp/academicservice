package com.kvs.academic.repo;

import com.kvs.academic.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {
    List<QuizQuestion> findByQuizIdOrderBySequenceNoAsc(UUID quizId);
    List<QuizQuestion> findByQuizIdIn(Collection<UUID> quizIds);
    long countByQuizId(UUID quizId);
}
