package com.example.demo.controller;

import com.example.demo.entity.Quiz;
import com.example.demo.repository.QuizRepository;
import com.example.demo.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/quiz")
@CrossOrigin(origins = "http://localhost:4200")
public class QuizController {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private QuizRepository quizRepository;

    @PostMapping("/{quizId}/upload")
    public ResponseEntity<String> uploadQuestions(
            @PathVariable Long quizId,
            @RequestParam("file")MultipartFile file,
            @RequestParam("deleteExisting") boolean deleteExisting){
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
    @GetMapping("/{id}")
    public ResponseEntity<Quiz> getQuiz(@PathVariable Long id){
        return quizRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
