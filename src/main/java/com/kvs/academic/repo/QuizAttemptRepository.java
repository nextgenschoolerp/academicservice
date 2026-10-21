package com.kvs.academic.repo;

import com.kvs.academic.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {
    List<QuizAttempt> findByQuizIdOrderByStartedAtAsc(UUID quizId);
    List<QuizAttempt> findByQuizIdAndStudentId(UUID quizId, UUID studentId);
    List<QuizAttempt> findByStudentIdIn(Collection<UUID> studentIds);
    boolean existsByQuizId(UUID quizId);
}
