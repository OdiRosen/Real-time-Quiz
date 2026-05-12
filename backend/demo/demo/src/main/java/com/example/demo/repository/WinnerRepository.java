package com.example.demo.repository;

import com.example.demo.model.QuizWinner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
// שימי לב שבסוגריים כתוב QuizWinner ולא משהו אחר כמו T או Object
public interface WinnerRepository extends JpaRepository<QuizWinner, Long> {
}