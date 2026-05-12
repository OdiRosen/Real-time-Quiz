package com.example.demo.controller;

import com.example.demo.model.Player;
import com.example.demo.model.Question;
import com.example.demo.service.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200", allowedHeaders = "*", allowCredentials = "true")
@RestController
@RequestMapping("/api/player")
public class PlayerController {

    @Autowired
    private PlayerService playerService;

    @PostMapping("/{quizId}/join")
    public ResponseEntity<Player> join(@PathVariable Long quizId,
                                       @RequestParam String name,
                                       @RequestParam String image) {
        try {
            Player player = playerService.joinQuiz(quizId, name, image);
            return ResponseEntity.ok(player);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
    }

    // FIX: מקבל playerId כדי לשלוף את השאלה האישית של כל שחקן
    @GetMapping("/question/{quizId}")
    public ResponseEntity<?> getSyncQuestion(
            @PathVariable Long quizId,
            @RequestParam String playerId) {
        try {
            Question question = playerService.getSyncQuestion(quizId, playerId);
            if (question == null) {
                return ResponseEntity.noContent().build(); // 204 = סיום חידון
            }
            return ResponseEntity.ok(question);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitAnswer(
            @RequestParam Long quizId,
            @RequestParam String playerId,
            @RequestParam String answer) {
        try {
            Map<String, Object> result = playerService.submitAnswer(quizId, playerId, answer);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/leaderboard/{quizId}")
    public ResponseEntity<List<Player>> getLeaderboard(@PathVariable Long quizId) {
        return ResponseEntity.ok(playerService.getActivePlayersSorted(quizId));
    }
}