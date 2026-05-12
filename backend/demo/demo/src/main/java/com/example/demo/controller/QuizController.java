package com.example.demo.controller;

import com.example.demo.model.Quiz;
import com.example.demo.repository.QuizRepository;
import com.example.demo.service.QuestionService;
import com.example.demo.service.QuizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // FIX: endpoint נפרד לשמירת זוכה — ללא בדיקת זמן
    @PatchMapping("/{id}/winner")
    public ResponseEntity<?> saveWinner(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            String winnerName = (String) body.get("winnerName");
            Integer winnerScore = (Integer) body.get("winnerScore");
            Quiz updated = quizService.saveWinner(id, winnerName, winnerScore);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
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