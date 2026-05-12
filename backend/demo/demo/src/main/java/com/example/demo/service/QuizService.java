package com.example.demo.service;

import com.example.demo.model.Quiz;
import com.example.demo.repository.QuizRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuizService {

    @Autowired
    private QuizRepository quizRepository;

    public Quiz addQuiz(Quiz quiz) {
        return quizRepository.save(quiz);
    }

    public List<Quiz> getQuizzesByEmail(String email) {
        return quizRepository.findByCreatorEmail(email);
    }

    public Quiz getQuizById(Long id) {
        return quizRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));
    }

    /**
     * עדכון שדות החידון (שם, זמנים) — מוגבל לחידונים פעילים בלבד
     */
    public Quiz updateQuiz(Long id, Quiz updatedQuiz) {
        Quiz existingQuiz = quizRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        // FIX: הבדיקה היא רק על עדכון שדות ניהוליים — לא על שמירת זוכה
        if (LocalDateTime.now().isAfter(existingQuiz.getEndTime())
                && updatedQuiz.getName() != null) {
            throw new RuntimeException("Cannot update a closed quiz");
        }

        if (updatedQuiz.getName() != null) {
            existingQuiz.setName(updatedQuiz.getName());
        }
        if (updatedQuiz.getStartTime() != null) {
            existingQuiz.setStartTime(updatedQuiz.getStartTime());
        }
        if (updatedQuiz.getEndTime() != null) {
            existingQuiz.setEndTime(updatedQuiz.getEndTime());
        }
        // שמירת זוכה — תמיד מותרת, ללא קשר לזמן
        if (updatedQuiz.getWinnerName() != null) {
            existingQuiz.setWinnerName(updatedQuiz.getWinnerName());
        }
        if (updatedQuiz.getWinnerScore() != null) {
            existingQuiz.setWinnerScore(updatedQuiz.getWinnerScore());
        }

        return quizRepository.save(existingQuiz);
    }

    /**
     * שמירת זוכה בלבד — ללא בדיקת זמן, תמיד מותר
     */
    public Quiz saveWinner(Long id, String name, Integer score) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        // כאן את מעדכנת את השדות ב-DB
        quiz.setWinnerName(name);
        quiz.setWinnerScore(score);
        return quizRepository.save(quiz);
    }
}