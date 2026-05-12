package com.example.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class QuizWinner {

    @Id // השורה הזו חסרה לך! היא קובעת שזה המפתח הראשי
    private Long quizId;

    private String playerName;
    private int score;
    private long totalTimeMillis;

    // קונסטרקטור ריק (חובה ל-JPA)
    public QuizWinner() {}

    // Getters and Setters
    public Long getQuizId() { return quizId; }
    public void setQuizId(Long quizId) { this.quizId = quizId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public long getTotalTimeMillis() { return totalTimeMillis; }
    public void setTotalTimeMillis(long totalTimeMillis) { this.totalTimeMillis = totalTimeMillis; }
}