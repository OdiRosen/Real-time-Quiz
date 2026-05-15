package com.example.demo.service;

import com.example.demo.model.Quiz;
import com.example.demo.model.QuizWinner;
import com.example.demo.repository.QuizRepository;
import com.example.demo.repository.WinnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class QuizService {

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private WinnerRepository winnerRepository;

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
     * שמירת זוכה ראשון (תאימות לאחור) + עדכון טבלת top 3
     */
    public Quiz saveWinner(Long id, String name, Integer score) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        quiz.setWinnerName(name);
        quiz.setWinnerScore(score);
        return quizRepository.save(quiz);
    }

    /**
     * שמירת עד 3 מנצחים בטבלה נפרדת (נשאר גם אחרי ששחקנים עוזבים)
     */
    @Transactional
    public List<QuizWinner> saveTopWinners(Long quizId, List<Map<String, Object>> winnersPayload) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        winnerRepository.deleteByQuizId(quizId);

        List<QuizWinner> saved = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : winnersPayload) {
            if (rank > 3) break;

            String name = row.get("playerName") != null ? row.get("playerName").toString().trim() : "";
            if (name.isEmpty()) continue;

            Object scoreObj = row.get("score");
            int score = scoreObj instanceof Number
                    ? ((Number) scoreObj).intValue()
                    : Integer.parseInt(scoreObj.toString());

            String image = row.get("image") != null ? row.get("image").toString() : "";

            QuizWinner w = new QuizWinner();
            w.setQuizId(quizId);
            w.setRank(rank);
            w.setPlayerName(name);
            w.setScore(score);
            w.setImage(image);
            saved.add(winnerRepository.save(w));

            if (rank == 1) {
                quiz.setWinnerName(name);
                quiz.setWinnerScore(score);
            }
            rank++;
        }

        quizRepository.save(quiz);
        return saved;
    }

    public List<QuizWinner> getTopWinners(Long quizId) {
        return winnerRepository.findByQuizIdOrderByRankAsc(quizId);
    }

    public void deleteQuiz(Long id) {
        if (!quizRepository.existsById(id)) {
            throw new RuntimeException("Quiz not found");
        }
        quizRepository.deleteById(id);
    }
}