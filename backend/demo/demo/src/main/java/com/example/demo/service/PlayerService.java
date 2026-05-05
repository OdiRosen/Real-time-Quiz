package com.example.demo.service;

import com.example.demo.entity.Player;
import com.example.demo.entity.Question;
import com.example.demo.entity.Quiz;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.QuizRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlayerService {

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    private final Map<Long, List<Player>> activePlayers = new ConcurrentHashMap<>();

    public boolean submitAnswer(Long quizId, String playerId, String submittedAnswer, long timeForThisQuestion){
        List<Player> players = activePlayers.get(quizId);
        Player player = players.stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElseThrow();
        List<Question> questions = questionRepository.findByQuizId(quizId);
        Question currentQuestion = questions.get(player.getCurrentQuestionIndex());

        boolean isCorrect = currentQuestion.getAnswer1().equals(submittedAnswer);

        if (isCorrect){
            player.setScore(player.getScore() + currentQuestion.getPoints());
        }
        player.setTotalTimeTaken(player.getTotalTimeTaken() + timeForThisQuestion);
        player.setCurrentQuestionIndex(player.getCurrentQuestionIndex() + 1);

        updateLeaderboardAndNotifyAsync(quizId, players);

        return isCorrect;
    }

    @Async("taskExecutor")
    public void updateLeaderboardAndNotifyAsync(Long quizId, List<Player> players){
        Player leader = players.stream()
                .sorted(Comparator.comparingInt(Player::getScore).reversed()
                        .thenComparingLong(Player::getTotalTimeTaken))
                        .findFirst()
                        .orElse(null);

        if (leader != null)
            System.out.println("The leader in this quiz" + quizId + "is: " + leader.getDisplayName() + "with " + leader.getScore() + "points");

    }

    public Player joinQuiz(Long quizId, String displayName, String image){
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(quiz.getStartTime()) || now.isAfter(quiz.getEndTime())){
            throw new RuntimeException("Sorry, the quiz close now");
        }

        Player newPlayer = new Player();
        newPlayer.setPlayerId(UUID.randomUUID().toString());
        newPlayer.setDisplayName(displayName);
        newPlayer.setImage(image);
        newPlayer.setScore(0);
        newPlayer.setCurrentQuestionIndex(0);

        activePlayers.computeIfAbsent(quizId, k -> new ArrayList<>()).add(newPlayer);

        return newPlayer;
    }

    public Question getNextQuestion(Long quizId, String playerId){
        List<Question> allQuestion = questionRepository.findByQuizId(quizId);
        Player player = findPlayer(quizId, playerId);

        if (player.getCurrentQuestionIndex() >= allQuestion.size()){
            throw new RuntimeException("You're finish all the questions!");
        }

        Question question = allQuestion.get(player.getCurrentQuestionIndex());

        return question;
    }

    public List<Player> getActivePlayers(Long quizId) {
        return activePlayers.getOrDefault(quizId, new ArrayList<>())
                .stream()
                .sorted(Comparator.comparingInt(Player::getScore).reversed()
                        .thenComparingLong(Player::getTotalTimeTaken))
                .toList();
    }

    private Player findPlayer(Long quizId, String playerId){
        return activePlayers.getOrDefault(quizId, new ArrayList<>())
                .stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Player not found in this game😒"));
    }
}
