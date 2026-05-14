package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // מומלץ להשתמש ב-Long כ-ID של מסד נתונים

    private String playerId; // ה-ID שאת משתמשת בו באפליקציה (אפשר להשאיר אותו)
    private String displayName;
    private String image;
    private int score;
    private int currentQuestionIndex = 0;
    private long totalTimeTaken;

    private boolean hasAnswered = false;
    private String lastAnswerStatus = "none";

    // בנאי ריק חובה ל-JPA
    public Player() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(int currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }

    public long getTotalTimeTaken() { return totalTimeTaken; }
    public void setTotalTimeTaken(long totalTimeTaken) { this.totalTimeTaken = totalTimeTaken; }

    public boolean isHasAnswered() { return hasAnswered; }
    public void setHasAnswered(boolean hasAnswered) { this.hasAnswered = hasAnswered; }

    public String getLastAnswerStatus() { return lastAnswerStatus; }
    public void setLastAnswerStatus(String lastAnswerStatus) { this.lastAnswerStatus = lastAnswerStatus; }
}