package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "quiz_winners")
public class QuizWinner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long quizId;

    /** דירוג 1–3 */
    private int rank;

    private String playerName;
    private int score;
    private String image;

    public QuizWinner() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getQuizId() { return quizId; }
    public void setQuizId(Long quizId) { this.quizId = quizId; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
}
