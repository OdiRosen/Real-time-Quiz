package com.example.demo.model;

public class Player {
    private String playerId;
    private String displayName;
    private String image;
    private int score;
    private int currentQuestionIndex = 0;
    private long totalTimeTaken;

    // שדות חדשים לאנימציות
    private boolean hasAnswered = false;
    private String lastAnswerStatus = "none"; // "correct", "wrong", "none"

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