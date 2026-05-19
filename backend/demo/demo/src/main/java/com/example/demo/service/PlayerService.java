package com.example.demo.service;

import com.example.demo.model.Player;
import com.example.demo.model.Question;
import com.example.demo.model.Quiz;
import com.example.demo.model.QuizWinner;
import com.example.demo.repository.QuizRepository;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.WinnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private SimpMessagingTemplate messagingTemplate;

    private final Map<Long, Map<String, Player>> activePlayers = new ConcurrentHashMap<>();
    private final Map<String, List<Question>> playerPersonalQuestions = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> questionStartTimes = new ConcurrentHashMap<>();
    private final Map<Long, Object> quizLocks = new ConcurrentHashMap<>();

    public Player joinQuiz(Long quizId, String displayName, String image, String reconnectPlayerId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        if (LocalDateTime.now().isAfter(quiz.getEndTime())) {
            throw new RuntimeException("החידון הסתיים, לא ניתן להצטרף.");
        }

        String normalizedName = normalizeName(displayName);
        if (normalizedName.isEmpty()) {
            throw new RuntimeException("יש להזין שם שחקן.");
        }

        synchronized (lockFor(quizId)) {
            Map<String, Player> playersInQuiz = activePlayers.computeIfAbsent(quizId, k -> new ConcurrentHashMap<>());

            if (reconnectPlayerId != null && !reconnectPlayerId.isBlank()) {
                Player reconnected = playersInQuiz.get(reconnectPlayerId.trim());
                if (reconnected != null) {
                    broadcastState(quizId);
                    return reconnected;
                }
            }

            Optional<Player> existingByName = playersInQuiz.values().stream()
                    .filter(p -> normalizeName(p.getDisplayName()).equals(normalizedName))
                    .findFirst();

            if (existingByName.isPresent()) {
                broadcastState(quizId);
                return existingByName.get();
            }

            Player newPlayer = new Player();
            newPlayer.setPlayerId(UUID.randomUUID().toString());
            newPlayer.setDisplayName(displayName.trim());
            newPlayer.setImage(image != null ? image : "");
            newPlayer.setScore(0);
            newPlayer.setCurrentQuestionIndex(0);
            newPlayer.setTotalTimeTaken(0L);
            newPlayer.setHasAnswered(false);
            newPlayer.setLastAnswerStatus("none");
            newPlayer.setFinished(false);

            List<Question> questions = new ArrayList<>(questionRepository.findByQuizId(quizId));
            if (questions.isEmpty()) {
                throw new RuntimeException("בחידון זה אין שאלות עדיין.");
            }
            Collections.shuffle(questions);
            playerPersonalQuestions.put(newPlayer.getPlayerId(), questions);

            playersInQuiz.put(newPlayer.getPlayerId(), newPlayer);
            questionStartTimes.put(newPlayer.getPlayerId(), LocalDateTime.now());

            System.out.println("שחקן " + displayName + " הצטרף לחידון " + quizId);

            refreshTop3AndSave(quizId);
            broadcastState(quizId);

            return newPlayer;
        }
    }

    public void removePlayer(Long quizId, String playerId) {
        Map<String, Player> playersInQuiz = activePlayers.get(quizId);
        if (playersInQuiz == null) return;

        synchronized (lockFor(quizId)) {
            Player leaving = playersInQuiz.get(playerId);
            if (leaving != null) {
                refreshTop3AndSave(quizId);
            }
            playersInQuiz.remove(playerId);
            playerPersonalQuestions.remove(playerId);
            questionStartTimes.remove(playerId);
            System.out.println("שחקן הוסר מהמערכת: " + playerId);
            broadcastState(quizId);
        }
    }

    private void broadcastState(Long quizId) {
        List<Player> active = getActivePlayersSorted(quizId);
        List<QuizWinner> podium = winnerRepository.findByQuizIdOrderByRankAsc(quizId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("active", active);
        payload.put("podium", podium);

        messagingTemplate.convertAndSend("/topic/quiz/" + quizId, payload);
    }

    public Question getSyncQuestion(Long quizId, String playerId) {
        Player player = findPlayer(quizId, playerId);
        if (player.isFinished()) {
            return null;
        }

        List<Question> personalList = playerPersonalQuestions.get(playerId);
        int currentIndex = player.getCurrentQuestionIndex();

        if (personalList == null || currentIndex >= personalList.size()) {
            player.setFinished(true);
            refreshTop3AndSave(quizId);
            broadcastState(quizId);
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
            player.setFinished(true);
            refreshTop3AndSave(quizId);
            broadcastState(quizId);
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

        if (player.getCurrentQuestionIndex() >= questions.size()) {
            player.setFinished(true);
            response.put("status", "finished");
        }

        questionStartTimes.put(playerId, LocalDateTime.now());

        refreshTop3AndSave(quizId);
        broadcastState(quizId);

        return response;
    }

    /**
     * ממזג שחקנים פעילים + רשומות DB, ממיין, שומר 3 מקומות (1–3) ומעדכן זוכה ראשון ב-quiz.
     */
    @Transactional
    public List<QuizWinner> refreshTop3AndSave(Long quizId) {
        Map<String, ScoreHolder> merged = new LinkedHashMap<>();

        for (QuizWinner w : winnerRepository.findByQuizIdOrderByRankAsc(quizId)) {
            mergeCandidate(merged, w.getPlayerName(), w.getScore(), w.getImage(), Long.MAX_VALUE);
        }

        Map<String, Player> inQuiz = activePlayers.getOrDefault(quizId, Collections.emptyMap());
        for (Player p : inQuiz.values()) {
            mergeCandidate(merged, p.getDisplayName(), p.getScore(), p.getImage(), p.getTotalTimeTaken());
        }

        List<ScoreHolder> top3 = merged.values().stream()
                .sorted(Comparator.comparingInt(ScoreHolder::score).reversed()
                        .thenComparingLong(ScoreHolder::timeTaken))
                .limit(3)
                .collect(Collectors.toList());

        winnerRepository.deleteByQuizId(quizId);

        List<QuizWinner> saved = new ArrayList<>();
        int rank = 1;
        for (ScoreHolder h : top3) {
            QuizWinner w = new QuizWinner();
            w.setQuizId(quizId);
            w.setRank(rank);
            w.setPlayerName(h.name);
            w.setScore(h.score);
            w.setImage(h.image != null ? h.image : "");
            saved.add(winnerRepository.save(w));

            if (rank == 1) {
                quizRepository.findById(quizId).ifPresent(quiz -> {
                    quiz.setWinnerName(h.name);
                    quiz.setWinnerScore(h.score);
                    quizRepository.save(quiz);
                });
            }
            rank++;
        }

        return saved;
    }

    public List<QuizWinner> getTopWinners(Long quizId) {
        return winnerRepository.findByQuizIdOrderByRankAsc(quizId);
    }

    public List<Player> getActivePlayersSorted(Long quizId) {
        Map<String, Player> playersInQuiz = activePlayers.getOrDefault(quizId, new HashMap<>());
        return playersInQuiz.values().stream()
                .filter(p -> !p.isFinished())
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

    private Object lockFor(Long quizId) {
        return quizLocks.computeIfAbsent(quizId, k -> new Object());
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private void mergeCandidate(Map<String, ScoreHolder> merged, String name, int score, String image, long timeTaken) {
        String key = normalizeName(name);
        if (key.isEmpty()) return;

        String display = name != null ? name.trim() : "";
        String img = image != null ? image : "";

        ScoreHolder existing = merged.get(key);
        if (existing == null || score > existing.score
                || (score == existing.score && timeTaken < existing.timeTaken)) {
            merged.put(key, new ScoreHolder(display, score, img, timeTaken));
        }
    }

    private static class ScoreHolder {
        final String name;
        final int score;
        final String image;
        final long timeTaken;

        ScoreHolder(String name, int score, String image, long timeTaken) {
            this.name = name;
            this.score = score;
            this.image = image;
            this.timeTaken = timeTaken;
        }

        int score() { return score; }
        long timeTaken() { return timeTaken; }
    }
}
