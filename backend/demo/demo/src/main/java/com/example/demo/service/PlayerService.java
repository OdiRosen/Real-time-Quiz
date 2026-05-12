package com.example.demo.service;

import com.example.demo.model.Player;
import com.example.demo.model.Question;
import com.example.demo.model.Quiz;
import com.example.demo.model.QuizWinner;
import com.example.demo.repository.QuizRepository;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.WinnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final Map<Long, Map<String, Player>> activePlayers = new ConcurrentHashMap<>();
    private final Map<String, List<Question>> playerPersonalQuestions = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> questionStartTimes = new ConcurrentHashMap<>();

    public Player joinQuiz(Long quizId, String displayName, String image) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(quiz.getEndTime())) {
            throw new RuntimeException("החידון הסתיים, לא ניתן להצטרף.");
        }

        Player newPlayer = new Player();
        newPlayer.setPlayerId(UUID.randomUUID().toString());
        newPlayer.setDisplayName(displayName);
        newPlayer.setImage(image);
        newPlayer.setScore(0);
        newPlayer.setCurrentQuestionIndex(0);
        newPlayer.setTotalTimeTaken(0L);

        List<Question> questions = new ArrayList<>(questionRepository.findByQuizId(quizId));
        if (questions.isEmpty()) {
            throw new RuntimeException("בחידון זה אין שאלות עדיין.");
        }
        Collections.shuffle(questions);
        playerPersonalQuestions.put(newPlayer.getPlayerId(), questions);

        activePlayers.computeIfAbsent(quizId, k -> new ConcurrentHashMap<>())
                .put(newPlayer.getPlayerId(), newPlayer);

        // אתחול טיימר לשאלה הראשונה
        questionStartTimes.put(newPlayer.getPlayerId(), LocalDateTime.now());

        return newPlayer;
    }

    public Question getSyncQuestion(Long quizId, String playerId) {
        Player player = findPlayer(quizId, playerId);
        List<Question> personalList = playerPersonalQuestions.get(playerId);
        int currentIndex = player.getCurrentQuestionIndex();

        if (personalList == null || currentIndex >= personalList.size()) {
            return null;
        }

        // תיקון הטיימר: מאפסים את הזמן ברגע שהשחקן "מושך" את השאלה החדשה מהשרת
        questionStartTimes.put(playerId, LocalDateTime.now());

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

        if ("TIMEOUT".equals(submittedAnswer) || secondsElapsed > SECONDS_PER_QUESTION) {
            response.put("status", "timeout");
            response.put("correct", false);
        } else {
            boolean isCorrect = currentQuestion.getAnswer1().trim()
                    .equalsIgnoreCase(submittedAnswer.trim());

            if (isCorrect) {
                long bonus = Math.max(0, SECONDS_PER_QUESTION - secondsElapsed);
                player.setScore(player.getScore() + currentQuestion.getPoints() + (int) bonus);
            }
            response.put("status", "success");
            response.put("correct", isCorrect);
        }

        // עדכון זמן מצטבר וקידום אינדקס
        player.setTotalTimeTaken(player.getTotalTimeTaken() + secondsElapsed);
        player.setCurrentQuestionIndex(player.getCurrentQuestionIndex() + 1);
        response.put("score", player.getScore());

        // בדיקה האם יש מנצח חדש אחרי כל תשובה
        updateWinnerIfNecessary(quizId, player);

        return response;
    }

    public void updateWinnerIfNecessary(Long quizId, Player currentPlayer) {
        // תיקון: משתמשים ב-winnerRepository שהזרקנו למעלה, לא מגדירים אותו מחדש בתוך הפונקציה
        QuizWinner currentWinner = (QuizWinner) winnerRepository.findById(quizId).orElse(null);

        long currentPlayerTime = currentPlayer.getTotalTimeTaken();
        int currentPlayerScore = currentPlayer.getScore();

        boolean isNewWinner = false;

        if (currentWinner == null) {
            isNewWinner = true;
        } else if (currentPlayerScore > currentWinner.getScore()) {
            isNewWinner = true;
        } else if (currentPlayerScore == currentWinner.getScore() &&
                currentPlayerTime < currentWinner.getTotalTimeMillis()) {
            isNewWinner = true;
        }

        if (isNewWinner) {
            QuizWinner newWinner = new QuizWinner();
            newWinner.setQuizId(quizId);
            newWinner.setPlayerName(currentPlayer.getDisplayName());
            newWinner.setScore(currentPlayerScore);
            newWinner.setTotalTimeMillis(currentPlayerTime);

            winnerRepository.save(newWinner);
        }
    }

    public List<Player> getActivePlayersSorted(Long quizId) {
        // שליפת המפה של השחקנים עבור החידון הספציפי
        Map<String, Player> playersInQuiz = activePlayers.getOrDefault(quizId, new HashMap<>());

        // מיון: קודם לפי ניקוד גבוה, ואז לפי זמן כולל נמוך (שובר שוויון)
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