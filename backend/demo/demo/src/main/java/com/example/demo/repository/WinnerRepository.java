package com.example.demo.repository;

import com.example.demo.model.QuizWinner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WinnerRepository extends JpaRepository<QuizWinner, Long> {

    List<QuizWinner> findByQuizIdOrderByRankAsc(Long quizId);

    void deleteByQuizId(Long quizId);
}
