package com.example.demo.controller;

import com.example.demo.model.Player; // וודאי שייבאת את ה-Model של השחקן
import com.example.demo.model.Quiz;
import com.example.demo.repository.QuizRepository;
import com.example.demo.service.PlayerService; // הוספנו את השירות של השחקנים
import com.example.demo.service.QuestionService;
import com.example.demo.service.QuizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/quiz")
@CrossOrigin(origins = "http://localhost:4200", allowedHeaders = "*", allowCredentials = "true")
public class QuizController {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizService quizService;

    @Autowired
    private PlayerService playerService; // הזרקת השירות לניהול שחקנים

    /**
     * פונקציית ה-WebSocket לניהול הצטרפות שחקנים
     * זה מה שמונע את הכפילויות ומאפשר לזהות ניתוקים
     */
    @MessageMapping("/quiz.join/{quizId}")
    public void joinQuiz(@DestinationVariable Long quizId,
                         @Payload Player player,
                         SimpMessageHeaderAccessor headerAccessor) {

        // 1. קריאה לפונקציה ששלחת (היא מצוינת, היא תחזיר שחקן קיים או חדש)
        Player joinedPlayer = playerService.joinQuiz(quizId, player.getDisplayName(), player.getImage());

        // 2. השורה הקריטית למחיקה: שמירת ה-ID בתוך ה-Session של ה-WebSocket
        if (headerAccessor.getSessionAttributes() != null) {
            headerAccessor.getSessionAttributes().put("playerId", joinedPlayer.getPlayerId());
            headerAccessor.getSessionAttributes().put("quizId", quizId);
        }

        System.out.println("Player connected: " + joinedPlayer.getDisplayName() + " with ID: " + joinedPlayer.getPlayerId());
    }
    // --- שאר הפונקציות הקיימות שלך ללא שינוי ---

    @PostMapping
    public ResponseEntity<Quiz> createQuiz(@RequestBody Quiz quiz) {
        try {
            Quiz saved = quizRepository.save(quiz);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Quiz>> getQuizzesByEmail(@RequestParam String email) {
        try {
            return ResponseEntity.ok(quizService.getQuizzesByEmail(email));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Quiz> getQuiz(@PathVariable Long id) {
        return quizRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateQuiz(@PathVariable Long id, @RequestBody Quiz updatedQuiz) {
        try {
            Quiz updated = quizService.updateQuiz(id, updatedQuiz);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/winner")
    public ResponseEntity<?> saveWinner(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            String winnerName = (String) body.get("winnerName");
            // המרה בטוחה ל-Integer
            Object scoreObj = body.get("winnerScore");
            Integer winnerScore = (scoreObj instanceof Integer) ? (Integer) scoreObj : Integer.parseInt(scoreObj.toString());

            Quiz updated = quizService.saveWinner(id, winnerName, winnerScore);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{quizId}/upload")
    public ResponseEntity<String> uploadQuestions(
            @PathVariable Long quizId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("deleteExisting") boolean deleteExisting) {
        try {
            Quiz quiz = quizRepository.findById(quizId)
                    .orElseThrow(() -> new RuntimeException("quiz not found"));
            questionService.importQuestionsFromExcel(file.getInputStream(), quiz, deleteExisting);
            return ResponseEntity.ok("Questions loaded successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error loading file: " + e.getMessage());
        }
    }
}