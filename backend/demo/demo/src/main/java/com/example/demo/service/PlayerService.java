package com.example.demo.service;

import com.example.demo.model.Player;
import com.example.demo.model.Question;
import com.example.demo.model.Quiz;
import com.example.demo.repository.QuizRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PlayerService {

    private static final int SECONDS_PER_QUESTION = 10;

    @Autowired
    private QuizRepository quizRepository;

    private List<Question> excelQuestions = new ArrayList<>();

    private final Map<Long, LocalDateTime> questionStartTimes = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Player>> activePlayers = new ConcurrentHashMap<>();
    private final Map<Long, Integer> currentQuizQuestionIndex = new ConcurrentHashMap<>();

    // FIX 1: סט של חידונים שהחלו רשמית (אחרי שמישהו הצטרף והתחיל)
    private final Set<Long> activeQuizIds = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        this.excelQuestions = loadQuestionFromExcel();
        System.out.println("Loaded " + excelQuestions.size() + " questions from Excel.");
    }

    private List<Question> loadQuestionFromExcel() {
        List<Question> questionList = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream("/Questions.xlsx")) {
            if (is == null) throw new RuntimeException("Could not find Questions.xlsx!");

            Workbook workbook = new XSSFWorkbook(is);
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || row.getCell(0) == null) continue;

                Question q = new Question();
                q.setQuestionText(formatter.formatCellValue(row.getCell(0)));
                q.setAnswer1(formatter.formatCellValue(row.getCell(1)));
                q.setAnswer2(formatter.formatCellValue(row.getCell(2)));
                q.setAnswer3(formatter.formatCellValue(row.getCell(3)));
                q.setAnswer4(formatter.formatCellValue(row.getCell(4)));

                // FIX 2: טיפול בטוח בתא הנקודות — לא קורסים אם ריק
                if (row.getCell(5) != null && row.getCell(5).getCellType() == CellType.NUMERIC) {
                    q.setPoints((int) row.getCell(5).getNumericCellValue());
                } else {
                    q.setPoints(10); // ברירת מחדל
                }

                questionList.add(q);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return questionList;
    }

    public Player joinQuiz(Long quizId, String displayName, String image) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(quiz.getStartTime()) || now.isAfter(quiz.getEndTime())) {
            throw new RuntimeException("Sorry, the quiz is currently closed");
        }

        long minutesRemaining = java.time.Duration.between(now, quiz.getEndTime()).toMinutes();
        long minimumRequiredMinutes = 5;
        if (minutesRemaining < minimumRequiredMinutes) {
            throw new RuntimeException("נשאר מעט מדי זמן לסיום החידון, לא ניתן להצטרף.");
        }

        Map<String, Player> playersInQuiz = activePlayers.computeIfAbsent(quizId, k -> new ConcurrentHashMap<>());
        Optional<Player> existingPlayer = playersInQuiz.values().stream()
                .filter(p -> p.getDisplayName() != null && p.getDisplayName().equalsIgnoreCase(displayName))
                .findFirst();

        if (existingPlayer.isPresent()) {
            Player playerToUpdate = existingPlayer.get();
            playerToUpdate.setImage(image);
            // Optionally update any other status fields here in the future.
            return playerToUpdate;
        }

        Player newPlayer = new Player();
        newPlayer.setPlayerId(UUID.randomUUID().toString());
        newPlayer.setDisplayName(displayName);
        newPlayer.setImage(image);
        newPlayer.setScore(0);
        newPlayer.setCurrentQuestionIndex(0);
        newPlayer.setTotalTimeTaken(0L);

        playersInQuiz.put(newPlayer.getPlayerId(), newPlayer);

        // FIX 3: אתחול החידון רק כשמצטרף שחקן ראשון — לא לפני כן
        if (!activeQuizIds.contains(quizId)) {
            activeQuizIds.add(quizId);
            currentQuizQuestionIndex.put(quizId, 0);
            questionStartTimes.put(quizId, LocalDateTime.now());
            System.out.println("חידון " + quizId + " הופעל — שחקן ראשון הצטרף.");
        }

        return newPlayer;
    }

    public Map<String, Object> submitAnswer(Long quizId, String playerId, String submittedAnswer) {
        Player player = findPlayer(quizId, playerId);
        int currentGlobalIdx = currentQuizQuestionIndex.getOrDefault(quizId, 0);

        Map<String, Object> response = new HashMap<>();

        // FIX 4: אם startTime אינו קיים — זו שגיאה לוגית, לא נאתחל עכשיו
        LocalDateTime startTime = questionStartTimes.get(quizId);
        if (startTime == null) {
            response.put("status", "error");
            response.put("message", "החידון טרם התחיל");
            return response;
        }

        long secondsElapsed = java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();

        if (secondsElapsed >= SECONDS_PER_QUESTION) {
            // הזמן פג — עוברים לשאלה הבאה עבור השחקן
            player.setCurrentQuestionIndex(currentGlobalIdx + 1);
            response.put("status", "timeout");
            response.put("correct", false);
            response.put("score", player.getScore());
            return response;
        }

        if (currentGlobalIdx >= excelQuestions.size()) {
            response.put("status", "finished");
            response.put("score", player.getScore());
            return response;
        }

        Question currentQuestion = excelQuestions.get(currentGlobalIdx);
        boolean isCorrect = currentQuestion.getAnswer1().trim().equalsIgnoreCase(submittedAnswer.trim());

        if (isCorrect) {
            // FIX 5: נקודות בונוס לפי מהירות — ככל שענו מהר יותר, מקבלים יותר
            long bonus = Math.max(0, SECONDS_PER_QUESTION - secondsElapsed);
            player.setScore(player.getScore() + currentQuestion.getPoints() + (int) bonus);
        }

        // FIX 6: עדכון הזמן הכולל שלקח לשחקן
        player.setTotalTimeTaken(player.getTotalTimeTaken() + secondsElapsed);
        player.setCurrentQuestionIndex(currentGlobalIdx + 1);

        response.put("status", "success");
        response.put("correct", isCorrect);
        response.put("score", player.getScore());
        return response;
    }

    @Async("taskExecutor")
    public void updateLeaderboardAndNotifyAsync(Long quizId) {
        List<Player> sortedPlayers = getActivePlayersSorted(quizId);
        Player leader = sortedPlayers.isEmpty() ? null : sortedPlayers.get(0);

        if (leader != null) {
            System.out.println("The leader in quiz " + quizId + " is: "
                    + leader.getDisplayName() + " with " + leader.getScore() + " points");
        }
    }

    public Question getSyncQuestion(Long quizId) {
        int currentIdx = currentQuizQuestionIndex.getOrDefault(quizId, 0);
        if (excelQuestions == null || excelQuestions.isEmpty()) {
            return null;
        }
        if (currentIdx >= excelQuestions.size()) {
            // FIX 7: מחזיר null בצורה מפורשת כדי שה-Controller יטפל בסיום
            return null;
        }
        return excelQuestions.get(currentIdx);
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
            throw new RuntimeException("Player not found in this game");
        }
        return playersInQuiz.get(playerId);
    }

    public void moveToNextQuestion(Long quizId) {
        int nextIdx = currentQuizQuestionIndex.getOrDefault(quizId, 0) + 1;
        currentQuizQuestionIndex.put(quizId, nextIdx);
        questionStartTimes.put(quizId, LocalDateTime.now());
    }

    // FIX 8: ה-Scheduler רץ רק על חידונים שהופעלו רשמית — לא על כולם
    @Scheduled(fixedRate = 1000)
    public void checkAndProgressQuizzes() {
        for (Long quizId : activeQuizIds) {
            LocalDateTime startTime = questionStartTimes.get(quizId);
            if (startTime == null) continue;

            long secondsElapsed = java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();

            if (secondsElapsed >= SECONDS_PER_QUESTION) {
                int nextIdx = currentQuizQuestionIndex.getOrDefault(quizId, 0) + 1;

                if (nextIdx >= excelQuestions.size()) {
                    // FIX 9: החידון נגמר — מסירים מה-activeQuizIds
                    activeQuizIds.remove(quizId);
                    System.out.println("חידון " + quizId + " הסתיים!");
                    continue;
                }

                currentQuizQuestionIndex.put(quizId, nextIdx);
                questionStartTimes.put(quizId, LocalDateTime.now());
                System.out.println("חידון " + quizId + " עבר אוטומטית לשאלה " + nextIdx);
            }
        }
    }
}