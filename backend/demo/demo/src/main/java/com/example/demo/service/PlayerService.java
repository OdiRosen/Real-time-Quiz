package com.example.demo.service;

import com.example.demo.model.Player;
import com.example.demo.model.Question;
import com.example.demo.model.Quiz;
import com.example.demo.model.QuizWinner;
import com.example.demo.repository.QuizRepository;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.WinnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate; // ייבוא חיוני לפתרון השגיאה
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PlayerService {

    private static final int SECONDS_PER_QUESTION = 10;

    @Autowired
    private WinnerRepository winnerRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate; // הזרקה חיונית לפתרון השגיאה

    private final Map<Long, Map<String, Player>> activePlayers = new ConcurrentHashMap<>();
    private final Map<String, List<Question>> playerPersonalQuestions = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> questionStartTimes = new ConcurrentHashMap<>();

    public Player joinQuiz(Long quizId, String displayName, String image) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        if (LocalDateTime.now().isAfter(quiz.getEndTime())) {
            throw new RuntimeException("החידון הסתיים, לא ניתן להצטרף.");
        }

        // בדיקה אם השם כבר קיים כדי למנוע כפילויות
        Map<String, Player> playersInQuiz = activePlayers.computeIfAbsent(quizId, k -> new ConcurrentHashMap<>());
        Optional<Player> existing = playersInQuiz.values().stream()
                .filter(p -> p.getDisplayName().equalsIgnoreCase(displayName))
                .findFirst();

        if (existing.isPresent()) {
            broadcastLeaderboard(quizId);
            return existing.get();
        }

        Player newPlayer = new Player();
        newPlayer.setPlayerId(UUID.randomUUID().toString());
        newPlayer.setDisplayName(displayName);
        newPlayer.setImage(image);
        newPlayer.setScore(0);
        newPlayer.setCurrentQuestionIndex(0);
        newPlayer.setTotalTimeTaken(0L);
        newPlayer.setHasAnswered(false);
        newPlayer.setLastAnswerStatus("none");

        List<Question> questions = new ArrayList<>(questionRepository.findByQuizId(quizId));
        if (questions.isEmpty()) {
            throw new RuntimeException("בחידון זה אין שאלות עדיין.");
        }
        Collections.shuffle(questions);
        playerPersonalQuestions.put(newPlayer.getPlayerId(), questions);

        playersInQuiz.put(newPlayer.getPlayerId(), newPlayer);
        questionStartTimes.put(newPlayer.getPlayerId(), LocalDateTime.now());

        System.out.println("שחקן " + displayName + " הצטרף לחידון " + quizId);

        // עדכון כל המחוברים שמישהו נכנס
        broadcastLeaderboard(quizId);

        return newPlayer;
    }

    public void removePlayer(Long quizId, String playerId) {
        Map<String, Player> playersInQuiz = activePlayers.get(quizId);
        if (playersInQuiz != null) {
            playersInQuiz.remove(playerId);
            System.out.println("שחקן הוסר מהמערכת: " + playerId);
            // עדכון כל המחוברים שמישהו יצא
            broadcastLeaderboard(quizId);
        }
    }

    private void broadcastLeaderboard(Long quizId) {
        List<Player> sortedPlayers = getActivePlayersSorted(quizId);
        // שליחת הרשימה המעודכנת לנתיב שהאנגולר מקשיב לו
        messagingTemplate.convertAndSend("/topic/quiz/" + quizId, sortedPlayers);
    }

    public Question getSyncQuestion(Long quizId, String playerId) {
        Player player = findPlayer(quizId, playerId);
        List<Question> personalList = playerPersonalQuestions.get(playerId);
        int currentIndex = player.getCurrentQuestionIndex();

        if (personalList == null || currentIndex >= personalList.size()) {
            return null;
        }

        player.setHasAnswered(false);
        player.setLastAnswerStatus("none");
        return personalList.get(currentIndex);
    }

    public Map<String, Object> submitAnswer(Long quizId, String playerId, String submittedAnswer) {
        Player player = findPlayer(quizId, playerId);
        List<Question> questions = playerPersonalQuestions.get(playerId);
        Map<String, Object> response = new HashMap<>();

        if (questions == null || player.getCurrentQuestionIndex() >= questions.size()) {
            response.put("status", "finished");
            return response;
        }

        Question currentQuestion = questions.get(player.getCurrentQuestionIndex());
        LocalDateTime startTime = questionStartTimes.getOrDefault(playerId, LocalDateTime.now());
        long secondsElapsed = java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();

        player.setHasAnswered(true);

        if ("TIMEOUT".equals(submittedAnswer) || secondsElapsed > SECONDS_PER_QUESTION) {
            player.setLastAnswerStatus("wrong");
            response.put("status", "timeout");
        } else {
            boolean isCorrect = currentQuestion.getAnswer1().trim().equalsIgnoreCase(submittedAnswer.trim());
            player.setLastAnswerStatus(isCorrect ? "correct" : "wrong");
            if (isCorrect) {
                long bonus = Math.max(0, SECONDS_PER_QUESTION - secondsElapsed);
                player.setScore(player.getScore() + currentQuestion.getPoints() + (int) bonus);
            }
            response.put("status", "success");
            response.put("correct", isCorrect);
        }

        player.setTotalTimeTaken(player.getTotalTimeTaken() + secondsElapsed);
        player.setCurrentQuestionIndex(player.getCurrentQuestionIndex() + 1);
        questionStartTimes.put(playerId, LocalDateTime.now());

        // עדכון טבלת המובילים אחרי כל תשובה
        broadcastLeaderboard(quizId);

        return response;
    }

    public List<Player> getActivePlayersSorted(Long quizId) {
        Map<String, Player> playersInQuiz = activePlayers.getOrDefault(quizId, new HashMap<>());
        return playersInQuiz.values().stream()
                .sorted(Comparator.comparingInt(Player::getScore).reversed()
                        .thenComparingLong(Player::getTotalTimeTaken))
                .collect(Collectors.toList());
    }

    private Player findPlayer(Long quizId, String playerId) {
        Map<String, Player> playersInQuiz = activePlayers.get(quizId);
        if (playersInQuiz == null || !playersInQuiz.containsKey(playerId)) {
            throw new RuntimeException("Player not found");
        }
        return playersInQuiz.get(playerId);
    }
}