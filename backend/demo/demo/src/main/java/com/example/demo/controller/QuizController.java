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

    // FIX: endpoint ליצירת חידון חדש — זה מה ש-Angular קורא
    @PostMapping
    public ResponseEntity<Quiz> createQuiz(@RequestBody Quiz quiz) {
        try {
            quiz.setId(null);
            Quiz saved = quizRepository.save(quiz);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // FIX: endpoint לקבלת כל החידונים של מנהל לפי אימייל
    @GetMapping
    public ResponseEntity<List<Quiz>> getQuizzesByEmail(@RequestParam String email) {
        try {
            List<Quiz> quizzes = quizService.getQuizzesByEmail(email);
            return ResponseEntity.ok(quizzes);
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

    // FIX: endpoint לעדכון חידון קיים
    @PutMapping("/{id}")
    public ResponseEntity<Quiz> updateQuiz(@PathVariable Long id, @RequestBody Quiz updatedQuiz) {
        try {
            Quiz updated = quizService.updateQuiz(id, updatedQuiz);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
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